package com.samjdtechnologies.answer42.ui.views;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.Validator;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Registration view for new user sign-up.
 */
@Route(UIConstants.ROUTE_REGISTER)
@PageTitle(UIConstants.TITLE_REGISTER)
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticationService authenticationService;
    
    private TextField usernameField;
    private EmailField emailField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private Button registerButton;
    private RouterLink loginLink;
    
    private Binder<RegistrationFormData> binder;

    public RegisterView(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        
        // Configure the layout
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName(UIConstants.CSS_FORM_LAYOUT);
        addClassName(UIConstants.CSS_REGISTER_FORM);
        
        // Create form components
        createTitle();
        createFormFields();
        createButtons();
        setupBinder();
    }
    
    private void createTitle() {
        // Create logo image
        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("80px");
        logo.setWidth("80px");
        
        // Create title
        H2 title = new H2(UIConstants.TITLE_REGISTER);
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
        usernameField.setPlaceholder("Choose a username");
        
        // Email field
        emailField = new EmailField(UIConstants.LABEL_EMAIL);
        emailField.setWidth(UIConstants.FORM_FIELD_WIDTH);
        emailField.setRequired(true);
        emailField.setRequiredIndicatorVisible(true);
        emailField.setPlaceholder("Enter your email address");
        
        // Password field
        passwordField = new PasswordField(UIConstants.LABEL_PASSWORD);
        passwordField.setWidth(UIConstants.FORM_FIELD_WIDTH);
        passwordField.setRequired(true);
        passwordField.setRequiredIndicatorVisible(true);
        passwordField.setPlaceholder("Create a password (min. 8 characters)");
        
        // Confirm password field
        confirmPasswordField = new PasswordField(UIConstants.LABEL_CONFIRM_PASSWORD);
        confirmPasswordField.setWidth(UIConstants.FORM_FIELD_WIDTH);
        confirmPasswordField.setRequired(true);
        confirmPasswordField.setRequiredIndicatorVisible(true);
        confirmPasswordField.setPlaceholder("Re-enter your password");
        
        add(usernameField, emailField, passwordField, confirmPasswordField);
    }
    
    private void createButtons() {
        // Register button
        registerButton = new Button(UIConstants.BTN_REGISTER, event -> register());
        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        registerButton.setWidth(UIConstants.FORM_FIELD_WIDTH);
        
        // Login link (link to login page)
        loginLink = new RouterLink(UIConstants.BTN_TO_LOGIN, LoginView.class);
        loginLink.addClassName(UIConstants.CSS_FOOTER);
        loginLink.getElement().getStyle().set("display", "block");
        loginLink.getElement().getStyle().set("margin-top", "1rem");
        loginLink.getElement().getStyle().set("text-align", "center");
        
        add(registerButton, loginLink);
    }
    
    private void setupBinder() {
        binder = new Binder<>(RegistrationFormData.class);
        
        // Username validation
        binder.forField(usernameField)
            .withValidator(new StringLengthValidator(UIConstants.VALIDATION_REQUIRED, 1, null))
            .withValidator((value, context) -> {
                if (value != null && !value.isEmpty() && authenticationService.usernameExists(value)) {
                    return ValidationResult.error("Username already exists");
                }
                return ValidationResult.ok();
            })
            .bind(RegistrationFormData::getUsername, RegistrationFormData::setUsername);
        
        // Email validation
        binder.forField(emailField)
            .withValidator(new StringLengthValidator(UIConstants.VALIDATION_REQUIRED, 1, null))
            .withValidator(new EmailValidator(UIConstants.VALIDATION_EMAIL_FORMAT))
            .withValidator((value, context) -> {
                if (value != null && !value.isEmpty() && authenticationService.emailExists(value)) {
                    return ValidationResult.error("Email already exists");
                }
                return ValidationResult.ok();
            })
            .bind(RegistrationFormData::getEmail, RegistrationFormData::setEmail);
        
        // Password validation
        binder.forField(passwordField)
            .withValidator(new StringLengthValidator(
                UIConstants.VALIDATION_PASSWORD_LENGTH, 8, null))
            .bind(RegistrationFormData::getPassword, RegistrationFormData::setPassword);
        
        // Confirm password validation
        binder.forField(confirmPasswordField)
            .withValidator(new PasswordMatchValidator())
            .bind(RegistrationFormData::getConfirmPassword, RegistrationFormData::setConfirmPassword);
    }
    
    private void register() {
        RegistrationFormData formData = new RegistrationFormData();
        
        if (binder.writeBeanIfValid(formData)) {
            try {
                boolean registered = authenticationService.register(
                    formData.getUsername(), 
                    formData.getEmail(), 
                    formData.getPassword()
                );
                
                if (registered) {
                    // Show success notification
                    Notification notification = Notification.show(
                        "Registration successful! You can now log in.");
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    notification.setPosition(Notification.Position.TOP_CENTER);
                    
                    // Navigate to login page
                    UI.getCurrent().navigate(UIConstants.ROUTE_LOGIN);
                } else {
                    // Show error notification
                    Notification notification = Notification.show(
                        "Registration failed. Please try again later.");
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    notification.setPosition(Notification.Position.TOP_CENTER);
                }
            } catch (Exception e) {
                // Show error notification with specific message
                Notification notification = Notification.show(
                    "Registration failed: " + e.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setPosition(Notification.Position.TOP_CENTER);
            }
        }
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // If user is already logged in, redirect to main view
        if (authenticationService.isAuthenticated()) {
            event.forwardTo(UIConstants.ROUTE_MAIN);
        }
        
        // Initialize form data
        binder.setBean(new RegistrationFormData());
    }
    
    /**
     * Validator for confirming that password fields match.
     */
    private class PasswordMatchValidator implements Validator<String> {
        @Override
        public ValidationResult apply(String confirmPassword, ValueContext context) {
            String password = passwordField.getValue();
            if (confirmPassword == null || !confirmPassword.equals(password)) {
                return ValidationResult.error("Passwords do not match");
            }
            return ValidationResult.ok();
        }
    }
    
    /**
     * Data class for the registration form.
     */
    public static class RegistrationFormData {
        private String username;
        private String email;
        private String password;
        private String confirmPassword;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getConfirmPassword() {
            return confirmPassword;
        }
        
        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }
}
