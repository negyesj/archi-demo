import { Component } from '@angular/core';

/** A DMS One színes „D” jele (a login-logo-dmsone.svg ikon-része). */
@Component({
  selector: 'app-dms-logo',
  template: `
    <svg [attr.width]="25" [attr.height]="24" viewBox="0 0 40 38" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="DMS One">
      <path d="M12.7347 0.00561523 H0 V10.3915 H12.7347 V0.00561523Z" fill="#ED6124" />
      <path d="M12.7347 10.3915H0V37.9942H12.7347V10.3915Z" fill="#FD8400" />
      <path d="M39.7206 18.8888C39.7206 17.6181 39.605 16.3929 39.379 15.2124C36.7853 21.9719 28.4511 27.0206 18.4208 27.3844H12.7344V37.994H20.2885C30.5243 38.257 39.9639 29.3782 39.7206 19.1108C39.7206 19.0361 39.7214 18.963 39.7206 18.8888Z" fill="#ED2447" />
      <path d="M24.4202 12.825C26.1898 14.5334 27.0134 16.6875 26.9502 19.1422C27.0507 22.7049 24.5829 25.9934 21.2904 27.1475C28.1025 26.2693 33.8739 23.2058 37.1926 19C38.1004 17.8487 38.848 16.582 39.377 15.2172C37.7431 6.49611 29.2558 -0.224631 20.3196 0.00574932H12.7344V10.3621C16.9158 10.5127 21.1623 9.49721 24.4202 12.8252V12.825Z" fill="#FCB200" />
    </svg>
  `,
  styles: [':host { display: inline-flex; align-items: center; }'],
})
export class DmsLogo {}
