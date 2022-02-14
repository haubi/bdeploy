import { Component, OnInit } from '@angular/core';
import {
  RouteConfigLoadEnd,
  RouteConfigLoadStart,
  Router,
} from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { NavAreasService } from './modules/core/services/nav-areas.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnInit {
  subscription: Subscription;
  loadCount = 0;

  constructor(private router: Router, private areas: NavAreasService) {
    console.log('----------------------------------------');
    console.log('BDeploy started...');
    console.log('----------------------------------------');

    // potential official fix for broken history on cancelled navigation, see https://github.com/angular/angular/issues/13586
    router.canceledNavigationResolution = 'computed';
  }

  ngOnInit() {
    this.subscription = this.areas.primaryRoute$.subscribe((url) => {
      if (!url) {
        return;
      }
      // safety, reduce load count, route is loaded so it cannot be loading anymore :)
      // this can happen if lazy loading is initiated but a guard redirects the router
      // somewhere else.
      this.decreaseLoadCount();
    });

    this.subscription.add(
      this.router.events
        .pipe(filter((e) => e instanceof RouteConfigLoadStart))
        .subscribe(() => {
          this.loadCount++;
        })
    );

    this.subscription.add(
      this.router.events
        .pipe(filter((e) => e instanceof RouteConfigLoadEnd))
        .subscribe(() => {
          this.decreaseLoadCount();
        })
    );
  }

  private decreaseLoadCount() {
    this.loadCount--;
    if (this.loadCount < 0) {
      this.loadCount = 0;
    }
  }
}
