import {
  HttpClient,
  HttpEventType,
  HttpHeaders,
  HttpParams,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { UploadInfoDto } from 'src/app/models/gen.dtos';
import { suppressGlobalErrorHandling } from '../utils/server.utils';

/** Enumeration of the possible states of an upload */
export enum UploadState {
  /** Files are transferred to the server */
  UPLOADING,

  /** Server side processing in progress */
  PROCESSING,

  /** Upload finished. No errors reported  */
  FINISHED,

  /** Upload failed. */
  FAILED,
}

/** Status of each file upload */
export class UploadStatus {
  file: File;

  /** The upload progress in percent (0-100)  */
  progressObservable: Observable<number>;

  /** Current state */
  state: UploadState;

  /** Notification when the state changes */
  stateObservable: Observable<UploadState>;

  /** The error message if failed. Or the response body if OK */
  detail: any;

  /** Activity scope ID */
  scope: string;

  /**
   * Progress Hint
   * @deprecated no longer used in new UI
   */
  processingHint: string;

  /** Cancels the ongoing request */
  cancel: () => void;
}

export interface UrlParameter {
  id: string;
  name: string;
  type: string;
  value: any;
}

export enum ImportState {
  /** Import in progress */
  IMPORTING,

  /** Import finished. No errors reported  */
  FINISHED,

  /** Import failed. */
  FAILED,
}

export class ImportStatus {
  filename: string;

  /** Current state */
  state: ImportState;

  /** Notification when the state changes */
  stateObservable: Observable<ImportState>;

  /** The error message if failed */
  detail: any;
}

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  constructor(private http: HttpClient) {}

  /**
   * Uploads the given files to the given URL and returns an observable result to track the upload status. For
   * each file a separate HTTP-POST request will be created.
   *
   *  @param url the target URL to post the files to
   *  @param files the files to upload
   *  @param urlParameter additional url parameter per file
   *  @param formDataParam the FormData's property name that holds the file
   *  @returns a map containing the upload status for each file
   */
  public upload(
    url: string,
    files: File[],
    urlParameter: UrlParameter[][],
    formDataParam: string
  ): Map<string, UploadStatus> {
    const result: Map<string, UploadStatus> = new Map();

    for (let i = 0; i < files.length; ++i) {
      const file = files[i];
      const params = urlParameter[i];

      result.set(file.name, this.uploadFile(url, file, params, formDataParam));
    }
    return result;
  }

  /**
   * Uploads the given file to the given URL and returns an observable result to track the upload status.
   *
   *  @param url the target URL to post the files to
   *  @param file the files to upload
   *  @param urlParameter additional url parameter per file
   *  @param formDataParam the FormData's property name that holds the file
   *  @returns a map containing the upload status for each file
   */
  public uploadFile(
    url: string,
    file: File,
    urlParameter: UrlParameter[],
    formDataParam: string
  ): UploadStatus {
    // create a new progress-subject for every file
    const uploadStatus = new UploadStatus();
    const progressSubject = new Subject<number>();
    const stateSubject = new Subject<UploadState>();
    uploadStatus.file = file;
    uploadStatus.progressObservable = progressSubject.asObservable();
    uploadStatus.stateObservable = stateSubject.asObservable();
    uploadStatus.stateObservable.subscribe((state) => {
      uploadStatus.state = state;
    });
    uploadStatus.scope = this.uuidv4();
    stateSubject.next(UploadState.UPLOADING);

    // create a new multipart-form for every file
    const formData: FormData = new FormData();
    formData.append(formDataParam, file, file.name);

    // Suppress global error handling and enable progress reporting
    const options = {
      reportProgress: true,
      headers: suppressGlobalErrorHandling(
        new HttpHeaders({ 'X-Proxy-Activity-Scope': uploadStatus.scope })
      ),
    };

    // create and set additional HttpParams
    if (urlParameter) {
      let httpParams = new HttpParams();
      urlParameter.forEach((p) => {
        if (p.type === 'boolean') {
          httpParams = httpParams.set(
            p.id,
            p.value === true ? 'true' : 'false'
          );
        } else {
          httpParams = httpParams.set(p.id, p.value);
        }
      });
      options['params'] = httpParams;
    }

    // create a http-post request and pass the form
    const req = new HttpRequest('POST', url, formData, options);
    const sub = this.http.request(req).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const percentDone = Math.round((100 * event.loaded) / event.total);
          progressSubject.next(percentDone);

          // Notify that upload is done and that server-side processing begins
          if (percentDone === 100) {
            progressSubject.complete();
            stateSubject.next(UploadState.PROCESSING);
          }
        } else if (event instanceof HttpResponse) {
          uploadStatus.detail = event.body;
          stateSubject.next(UploadState.FINISHED);
          stateSubject.complete();
        }
      },
      error: (error) => {
        uploadStatus.detail =
          error.statusText + ' (Status ' + error.status + ')';
        stateSubject.next(UploadState.FAILED);
        progressSubject.complete();
        stateSubject.complete();
      },
    });
    uploadStatus.cancel = () => sub.unsubscribe();
    return uploadStatus;
  }

  public importFile(url: string, dto: UploadInfoDto): ImportStatus {
    const importStatus = new ImportStatus();
    const stateSubject = new Subject<ImportState>();
    importStatus.filename = dto.filename;
    importStatus.stateObservable = stateSubject.asObservable();
    importStatus.stateObservable.subscribe((state) => {
      importStatus.state = state;
    });
    stateSubject.next(ImportState.IMPORTING);

    this.http
      .post<UploadInfoDto>(url, dto, {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      })
      .subscribe({
        next: (d) => {
          importStatus.detail = d.details;
          stateSubject.next(ImportState.FINISHED);
          stateSubject.complete();
        },
        error: (error) => {
          importStatus.detail =
            error.statusText + ' (Status ' + error.status + ')';
          stateSubject.next(ImportState.FAILED);
          stateSubject.complete();
        },
      });
    return importStatus;
  }

  uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
      /[xy]/g,
      function (c) {
        // tslint:disable-next-line:no-bitwise
        const r = (Math.random() * 16) | 0,
          // tslint:disable-next-line:no-bitwise
          v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
      }
    );
  }
}
