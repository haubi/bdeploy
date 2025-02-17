import { DragDropModule } from '@angular/cdk/drag-drop';
import { LayoutModule } from '@angular/cdk/layout';
import { CdkScrollableModule } from '@angular/cdk/scrolling';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { APP_INITIALIZER, ErrorHandler, NgModule } from '@angular/core';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatRippleModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTreeModule } from '@angular/material/tree';
import { RouterModule } from '@angular/router';
import { NgTerminalModule } from 'ng-terminal';
import { CookieService } from 'ngx-cookie-service';
import { GravatarModule } from 'ngx-gravatar';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { GlobalErrorHandler } from 'src/app/modules/core/global-error-handler';
import { BdActionRowComponent } from './components/bd-action-row/bd-action-row.component';
import { BdActivitiesComponent } from './components/bd-activities/bd-activities.component';
import { BdBannerComponent } from './components/bd-banner/bd-banner.component';
import { BdButtonPopupComponent } from './components/bd-button-popup/bd-button-popup.component';
import { BdButtonComponent } from './components/bd-button/bd-button.component';
import { BdDataCardComponent } from './components/bd-data-card/bd-data-card.component';
import { BdDataComponentCellComponent } from './components/bd-data-component-cell/bd-data-component-cell.component';
import { BdDataDateCellComponent } from './components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataDisplayComponent } from './components/bd-data-display/bd-data-display.component';
import { BdDataGridComponent } from './components/bd-data-grid/bd-data-grid.component';
import { BdDataGroupingPanelComponent } from './components/bd-data-grouping-panel/bd-data-grouping-panel.component';
import { BdDataGroupingComponent } from './components/bd-data-grouping/bd-data-grouping.component';
import { BdDataIconCellComponent } from './components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from './components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { BdDataPopoverCellComponent } from './components/bd-data-popover-cell/bd-data-popover-cell.component';
import { BdDataSizeCellComponent } from './components/bd-data-size-cell/bd-data-size-cell.component';
import { BdDataSvgIconCellComponent } from './components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { BdDataSyncCellComponent } from './components/bd-data-sync-cell/bd-data-sync-cell.component';
import { BdDataTableComponent } from './components/bd-data-table/bd-data-table.component';
import { BdDataUserAvatarCellComponent } from './components/bd-data-user-avatar-cell/bd-data-user-avatar-cell.component';
import { BdDialogContentComponent } from './components/bd-dialog-content/bd-dialog-content.component';
import { BdDialogMessageComponent } from './components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from './components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from './components/bd-dialog/bd-dialog.component';
import { BdEditorDiffComponent } from './components/bd-editor-diff/bd-editor-diff.component';
import { BdEditorComponent } from './components/bd-editor/bd-editor.component';
import { BdExpandButtonComponent } from './components/bd-expand-button/bd-expand-button.component';
import { BdFileDropComponent } from './components/bd-file-drop/bd-file-drop.component';
import { BdFileUploadRawComponent } from './components/bd-file-upload-raw/bd-file-upload-raw.component';
import { BdFileUploadComponent } from './components/bd-file-upload/bd-file-upload.component';
import { BdFormInputComponent } from './components/bd-form-input/bd-form-input.component';
import { BdFormSelectComponentOptionComponent } from './components/bd-form-select-component-option/bd-form-select-component-option.component';
import { BdFormSelectComponent } from './components/bd-form-select/bd-form-select.component';
import { BdFormToggleComponent } from './components/bd-form-toggle/bd-form-toggle.component';
import { BdImageUploadComponent } from './components/bd-image-upload/bd-image-upload.component';
import { BdLoadingOverlayComponent } from './components/bd-loading-overlay/bd-loading-overlay.component';
import { BdLogoComponent } from './components/bd-logo/bd-logo.component';
import { BdMicroIconButtonComponent } from './components/bd-micro-icon-button/bd-micro-icon-button.component';
import { BdNoDataComponent } from './components/bd-no-data/bd-no-data.component';
import { BdNotificationCardComponent } from './components/bd-notification-card/bd-notification-card.component';
import { BdPanelButtonComponent } from './components/bd-panel-button/bd-panel-button.component';
import { BdPopupDirective } from './components/bd-popup/bd-popup.directive';
import { BdSearchFieldComponent } from './components/bd-search-field/bd-search-field.component';
import { BdServerSyncButtonComponent } from './components/bd-server-sync-button/bd-server-sync-button.component';
import { BdTerminalComponent } from './components/bd-terminal/bd-terminal.component';
import { ConnectionLostComponent } from './components/connection-lost/connection-lost.component';
import { ConnectionVersionComponent } from './components/connection-version/connection-version.component';
import { LoginComponent } from './components/login/login.component';
import { MainNavButtonComponent } from './components/main-nav-button/main-nav-button.component';
import { MainNavContentComponent } from './components/main-nav-content/main-nav-content.component';
import { MainNavFlyinComponent } from './components/main-nav-flyin/main-nav-flyin.component';
import { MainNavMenuComponent } from './components/main-nav-menu/main-nav-menu.component';
import { MainNavTopComponent } from './components/main-nav-top/main-nav-top.component';
import { MainNavComponent } from './components/main-nav/main-nav.component';
import { UserAvatarComponent } from './components/user-avatar/user-avatar.component';
import { ClickStopPropagationDirective } from './directives/click-stop-propagation.directive';
import { FileDropDirective } from './directives/file-drop.directive';
import { httpInterceptorProviders } from './interceptors';
import { VersionShortPipe } from './pipes/version-short.pipe';
import { VersionPipe } from './pipes/version.pipe';
import { ConfigService } from './services/config.service';
import { EditItemInListValidatorDirective } from './validators/edit-item-in-list';
import { EditUniqueValueValidatorDirective } from './validators/edit-unique-value.directive';
import { IdentifierValidator } from './validators/identifier.directive';
import { PasswordVerificationValidator } from './validators/password-verification.directive';
import { PortValueValidatorDirective } from './validators/port-value.directive';
import { TrimmedValidator } from './validators/trimmed.directive';

export function loadAppConfig(cfgService: ConfigService) {
  return () => cfgService.load();
}

@NgModule({
  declarations: [
    MainNavComponent,
    BdLogoComponent,
    FileDropDirective,
    ClickStopPropagationDirective,
    ConnectionLostComponent,
    LoginComponent,
    UserAvatarComponent,
    VersionPipe,
    VersionShortPipe,
    MainNavTopComponent,
    MainNavFlyinComponent,
    MainNavMenuComponent,
    MainNavContentComponent,
    BdSearchFieldComponent,
    BdButtonComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataCardComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
    BdDataGroupingPanelComponent,
    BdLoadingOverlayComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MainNavButtonComponent,
    BdNoDataComponent,
    BdPanelButtonComponent,
    BdImageUploadComponent,
    BdActionRowComponent,
    BdFormInputComponent,
    IdentifierValidator,
    TrimmedValidator,
    BdFormToggleComponent,
    BdFormSelectComponent,
    BdDataComponentCellComponent,
    BdFileDropComponent,
    BdFileUploadComponent,
    BdFileUploadRawComponent,
    BdMicroIconButtonComponent,
    BdNotificationCardComponent,
    BdDialogMessageComponent,
    PasswordVerificationValidator,
    ConnectionVersionComponent,
    BdServerSyncButtonComponent,
    BdTerminalComponent,
    BdBannerComponent,
    BdDataSvgIconCellComponent,
    BdDataIconCellComponent,
    BdPopupDirective,
    BdDataSyncCellComponent,
    BdEditorComponent,
    BdEditorDiffComponent,
    BdActivitiesComponent,
    BdDataUserAvatarCellComponent,
    BdFormSelectComponentOptionComponent,
    BdDataPermissionLevelCellComponent,
    EditUniqueValueValidatorDirective,
    BdDataDateCellComponent,
    BdExpandButtonComponent,
    BdDataSizeCellComponent,
    BdDataPopoverCellComponent,
    PortValueValidatorDirective,
    EditItemInListValidatorDirective,
  ],
  providers: [
    httpInterceptorProviders,
    CookieService,
    /* make sure that ConfigService and HistoryService are initialize always on startup */
    {
      provide: APP_INITIALIZER,
      useFactory: loadAppConfig,
      deps: [ConfigService],
      multi: true,
    },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
  imports: [
    CommonModule,
    DragDropModule,
    CdkScrollableModule,

    // angular material components used in our own components.
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatSnackBarModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatDividerModule,
    MatAutocompleteModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatRippleModule,
    MatSlideToggleModule,
    MatBadgeModule,
    MatTreeModule,
    MatSortModule,
    MatTabsModule,
    MatExpansionModule,

    // angular base infrastructure used throughout the application.
    HttpClientModule,
    FormsModule,
    FlexLayoutModule,
    RouterModule,
    LayoutModule,

    // additional libraries used to provide cool UI :)
    GravatarModule,
    NgTerminalModule,
    MonacoEditorModule.forRoot(),
  ],
  exports: [
    // core infrastructure usable by any other module.
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    LayoutModule,

    // angular material things we don't want to re-import in *every* module
    MatIconModule,
    MatDividerModule,
    MatTooltipModule,

    // our own exported components.
    MainNavComponent,
    FileDropDirective,
    ClickStopPropagationDirective,
    LoginComponent,
    UserAvatarComponent,
    VersionPipe,
    VersionShortPipe,
    BdPopupDirective,
    BdButtonComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
    BdLoadingOverlayComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdNoDataComponent,
    BdPanelButtonComponent,
    BdImageUploadComponent,
    BdActionRowComponent,
    BdFormInputComponent,
    BdFormToggleComponent,
    BdFormSelectComponent,
    BdFileDropComponent,
    BdFileUploadComponent,
    BdFileUploadRawComponent,
    BdMicroIconButtonComponent,
    BdNotificationCardComponent,
    BdServerSyncButtonComponent,
    BdTerminalComponent,
    BdBannerComponent,
    BdDataSvgIconCellComponent,
    BdDataIconCellComponent,
    BdDataSyncCellComponent,
    BdEditorComponent,
    BdEditorDiffComponent,
    BdDataPermissionLevelCellComponent,
    BdDataSizeCellComponent,
    BdDataPopoverCellComponent,

    // validators
    IdentifierValidator,
    TrimmedValidator,
    PasswordVerificationValidator,
    EditUniqueValueValidatorDirective,
    BdExpandButtonComponent,
    PortValueValidatorDirective,
    EditItemInListValidatorDirective,
  ],
})
export class CoreModule {}
