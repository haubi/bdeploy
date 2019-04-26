import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';

// keep in sync with app-theme.scss
enum Theme {
  DEFAULT = 'app-light-theme',
  LIGHT_YELLOW = 'app-light-yellow-theme',
  DARK = 'app-dark-theme',
  DARK_YELLOW = 'app-dark-yellow-theme'
}

@Injectable({
  providedIn: 'root',
})
export class ThemeService {

  constructor(@Inject(DOCUMENT) private document: Document) {
    if (localStorage.getItem('theme') === null) {
      localStorage.setItem('theme', Theme.DEFAULT);
    }
    this.updateTheme(localStorage.getItem('theme') as Theme);
  }

  public getThemes(): Theme[] {
    return Object.values(Theme);
  }

  public getCurrentTheme(): Theme {
    return localStorage.getItem('theme') as Theme;
  }

  public updateTheme(theme: Theme) {
    localStorage.setItem('theme', theme);

    for (const v of Object.values(Theme)) {
      this.document.body.classList.remove(v);
    }

    this.document.body.classList.add(theme);
  }

}
