package com.samjdtechnologies.answer42.ui.views;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Login view for user authentication.
 */
@Route(UIConstants.ROUTE_LOGIN)
@PageTitle(UIConstants.TITLE_LOGIN)
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticationService authenticationService;
    
    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginButton;
    private RouterLink registerLink;

    /**
     * Constructs the login view with necessary authentication service dependency.
     * Initializes the login form with username and password fields.
     * 
     * @param authenticationService the service for authentication operations including login
     */
    public LoginView(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        
        // Configure the layout
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName(UIConstants.CSS_FORM_LAYOUT);
        
        // Create form components
        createTitle();
        createFormFields();
        createButtons();
    }
    
    private void createTitle() {
        // Create logo image
        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("80px");
        logo.setWidth("80px");
        
        // Create title
        H2 title = new H2(UIConstants.TITLE_LOGIN);
        title.addClassName(UIConstants.CSS_HEADER);
        
        // Center the logo above the title
        HorizontalLayout logoLayout = new HorizontalLayout(logo);
        logoLayout.setWidthFull();
        logoLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        
        add(logoLayout, title);
    }
    
    private void createFormFields() {
        // Username field
        usernameField = new TextField(UIConstants.LABEL_USERNAME);
        usernameField.setWidth(UIConstants.FORM_FIELD_WIDTH);
        usernameField.setRequired(true);
        usernameField.setRequiredIndicatorVisible(true);
        usernameField.setPlaceholder("Enter your username");
        
        // Password field
        passwordField = new PasswordField(UIConstants.LABEL_PASSWORD);
        passwordField.setWidth(UIConstants.FORM_FIELD_WIDTH);
        passwordField.setRequired(true);
        passwordField.setRequiredIndicatorVisible(true);
        passwordField.setPlaceholder("Enter your password");
        
        add(usernameField, passwordField);
    }
    
    private void createButtons() {
        // Login button
        loginButton = new Button(UIConstants.BTN_LOGIN, event -> login());
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidth(UIConstants.FORM_FIELD_WIDTH);
        
        // Register link (link to registration page)
        registerLink = new RouterLink(UIConstants.BTN_TO_REGISTER, RegisterView.class);
        registerLink.addClassName(UIConstants.CSS_FOOTER);
        registerLink.getElement().getStyle().set("display", "block");
        registerLink.getElement().getStyle().set("margin-top", "1rem");
        registerLink.getElement().getStyle().set("text-align", "center");
        
        add(loginButton, registerLink);
    }
    
    private void login() {
        if (!validateForm()) {
            return;
        }
        
        String username = usernameField.getValue();
        String password = passwordField.getValue();
        
        authenticationService.login(username, password)
            .ifPresentOrElse(
                token -> {
                    // Navigate to main view on successful login
                    UI.getCurrent().navigate(UIConstants.ROUTE_MAIN);
                },
                () -> {
                    // Show error notification on failed login
                    Notification notification = Notification.show("Invalid username or password");
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    notification.setPosition(Notification.Position.TOP_CENTER);
                }
            );
    }
    
    private boolean validateForm() {
        boolean valid = true;
        
        // Validate username
        if (usernameField.isEmpty()) {
            usernameField.setErrorMessage(UIConstants.VALIDATION_REQUIRED);
            usernameField.setInvalid(true);
            valid = false;
        } else {
            usernameField.setInvalid(false);
        }
        
        // Validate password
        if (passwordField.isEmpty()) {
            passwordField.setErrorMessage(UIConstants.VALIDATION_REQUIRED);
            passwordField.setInvalid(true);
            valid = false;
        } else {
            passwordField.setInvalid(false);
        }
        
        return valid;
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // If user is already logged in, redirect to main view
        if (authenticationService.isAuthenticated()) {
            event.forwardTo(UIConstants.ROUTE_MAIN);
        }
    }
}
