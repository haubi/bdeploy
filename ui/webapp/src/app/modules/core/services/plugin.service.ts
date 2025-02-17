import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { Api } from '../plugins/plugin.api';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class PluginService {
  constructor(private http: HttpClient, private config: ConfigService) {}

  public getEditorPlugin(
    group: string,
    product: ManifestKey,
    editorType: string
  ): Observable<PluginInfoDto> {
    const url =
      this.config.config.api +
      '/plugin-admin/get-editor/' +
      group +
      '/' +
      editorType;
    return this.http.post<PluginInfoDto>(url, product, {
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public load(plugin: PluginInfoDto, modulePath: string): Promise<any> {
    // Note: webpackIgnore is extremely important, otherwise webpack tries to resolve the import locally at build time.
    return import(
      /* webpackIgnore: true */ this.config.getPluginUrl(plugin) + modulePath
    );
  }

  private buildPluginUrl(plugin: PluginInfoDto, path: string): string {
    return (
      this.config.getPluginUrl(plugin) +
      (path.startsWith('/') ? path : '/' + path)
    );
  }

  public getApi(plugin: PluginInfoDto): Api {
    // Note: it is very important to *NOT* use 'this' in the returned object, as 'this' will be a *very* different
    // object than expected when those methods are actually called. we need to alias this to a delegate.

    // eslint-disable-next-line @typescript-eslint/no-this-alias
    const delegate = this;
    return {
      get(path, params?) {
        return firstValueFrom(
          delegate.http.get(delegate.buildPluginUrl(plugin, path), {
            params: params,
            responseType: 'text',
          })
        );
      },
      put(path, body, params?) {
        return firstValueFrom(
          delegate.http.put(delegate.buildPluginUrl(plugin, path), body, {
            params: params,
            responseType: 'text',
          })
        );
      },
      post(path, body, params?) {
        return firstValueFrom(
          delegate.http.post(delegate.buildPluginUrl(plugin, path), body, {
            params: params,
            responseType: 'text',
          })
        );
      },
      delete(path, params?) {
        return firstValueFrom(
          delegate.http.delete(delegate.buildPluginUrl(plugin, path), {
            params: params,
            responseType: 'text',
          })
        );
      },
      getResourceUrl() {
        return delegate.config.getPluginUrl(plugin);
      },
    };
  }
}
