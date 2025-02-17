import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { AuthenticationService } from '../services/authentication.service';

@Injectable({
  providedIn: 'root',
})
export class AdminGuard implements CanActivate {
  constructor(
    private authService: AuthenticationService,
    private snackbar: MatSnackBar,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (!this.authService.isGlobalAdmin()) {
      this.snackbar.open(
        `Unfortunately, ${route.url.join(
          '/'
        )} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
        'DISMISS',
        { panelClass: 'error-snackbar' }
      );
      this.router.navigate(['/groups/browser']);
      return false;
    }
    return true;
  }
}
