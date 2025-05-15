import { unsafeCSS, registerStyles } from '@vaadin/vaadin-themable-mixin/register-styles';

import mainLayoutCss from 'themes/answer42/components/main-layout.css?inline';
import dashboardCss from 'themes/answer42/components/dashboard.css?inline';
import authFormsCss from 'themes/answer42/components/auth-forms.css?inline';


if (!document['_vaadintheme_answer42_componentCss']) {
  registerStyles(
        'main-layout',
        unsafeCSS(mainLayoutCss.toString())
      );
      registerStyles(
        'dashboard',
        unsafeCSS(dashboardCss.toString())
      );
      registerStyles(
        'auth-forms',
        unsafeCSS(authFormsCss.toString())
      );
      
  document['_vaadintheme_answer42_componentCss'] = true;
}

if (import.meta.hot) {
  import.meta.hot.accept((module) => {
    window.location.reload();
  });
}

