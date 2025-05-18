package com.samjdtechnologies.answer42.ui.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * ProfileView displays user profile information and allows updates to profile settings.
 */
@Route(value = UIConstants.ROUTE_PROFILE, layout = MainLayout.class)
@PageTitle("Answer42 - Profile")
@Secured("ROLE_USER")
public class ProfileView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileView.class);
    
    private final UserService userService;
    private User currentUser;
    
    private TextField fullNameField;
    private Button saveChangesButton;

    public ProfileView(UserService userService) {
        this.userService = userService;
        
        LoggingUtil.debug(LOG, "ProfileView", "ProfileView initialized");
        addClassName(UIConstants.CSS_PROFILE_VIEW);
        setSizeFull();
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing profile view components");
        
        // Title
        H1 profileTitle = new H1("Your Profile");
        profileTitle.addClassName(UIConstants.CSS_PROFILE_TITLE);
        
        // Profile form
        VerticalLayout profileForm = createProfileForm();
        
        // Add all components to the view
        add(profileTitle, profileForm);
        
        LoggingUtil.debug(LOG, "initializeView", "Profile view components initialized");
    }
    
    private VerticalLayout createProfileForm() {
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setPadding(false);
        formLayout.setSpacing(true);
        
        // Email field (read-only)
        Div emailSection = new Div();
        emailSection.addClassName(UIConstants.CSS_PROFILE_SECTION);
        
        H3 emailLabel = new H3("Email");
        
        TextField emailField = new TextField();
        emailField.setValue(currentUser != null ? currentUser.getEmail() : "");
        emailField.setReadOnly(true);
        emailField.setWidthFull();
        
        Span emailHelpText = new Span("Email cannot be changed. Contact support for assistance.");
        emailHelpText.addClassName(UIConstants.CSS_HELP_TEXT);
        
        emailSection.add(emailLabel, emailField, emailHelpText);
        
        // Full Name field
        Div nameSection = new Div();
        nameSection.addClassName(UIConstants.CSS_PROFILE_SECTION);
        
        H3 nameLabel = new H3("Full Name");
        
        fullNameField = new TextField();
        fullNameField.setValue(currentUser != null ? currentUser.getUsername() : "");
        fullNameField.setWidthFull();
        
        nameSection.add(nameLabel, fullNameField);
        
        // Account Information section
        Div accountInfoSection = new Div();
        accountInfoSection.addClassName(UIConstants.CSS_PROFILE_SECTION);
        
        H3 accountInfoLabel = new H3("Account Information");
        
        // Account type
        HorizontalLayout accountTypeLayout = new HorizontalLayout();
        accountTypeLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        accountTypeLayout.setSpacing(true);
        
        Span accountTypeLabel = new Span("Account Type:");
        accountTypeLabel.addClassName(UIConstants.CSS_INFO_LABEL);
        
        String accountType = "Free";
        if (currentUser != null && currentUser.getRoles() != null) {
            if (currentUser.getRoles().contains("ROLE_PREMIUM")) {
                accountType = "Premium";
            } else if (currentUser.getRoles().contains("ROLE_ADMIN")) {
                accountType = "Admin";
            }
        }
        
        Span accountTypeValue = new Span(accountType);
        accountTypeValue.addClassName(UIConstants.CSS_INFO_VALUE);
        
        accountTypeLayout.add(accountTypeLabel, accountTypeValue);
        
        // Member since
        HorizontalLayout memberSinceLayout = new HorizontalLayout();
        memberSinceLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        memberSinceLayout.setSpacing(true);
        
        Span memberSinceLabel = new Span("Member Since:");
        memberSinceLabel.addClassName(UIConstants.CSS_INFO_LABEL);
        
        // Use a static date for the mockup - in a real implementation this would come from the user object
        Span memberSinceValue = new Span("4/7/2025");
        memberSinceValue.addClassName(UIConstants.CSS_INFO_VALUE);
        
        memberSinceLayout.add(memberSinceLabel, memberSinceValue);
        
        // Last login
        HorizontalLayout lastLoginLayout = new HorizontalLayout();
        lastLoginLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        lastLoginLayout.setSpacing(true);
        
        Span lastLoginLabel = new Span("Last Login:");
        lastLoginLabel.addClassName(UIConstants.CSS_INFO_LABEL);
        
        // Format current date for the demo - in real implementation this would come from auth data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
        String formattedDate = LocalDateTime.now().format(formatter);
        
        Span lastLoginValue = new Span(formattedDate);
        lastLoginValue.addClassName(UIConstants.CSS_INFO_VALUE);
        
        lastLoginLayout.add(lastLoginLabel, lastLoginValue);
        
        accountInfoSection.add(accountInfoLabel, accountTypeLayout, memberSinceLayout, lastLoginLayout);
        
        // Save button
        saveChangesButton = new Button("Save Changes");
        saveChangesButton.addClassName(UIConstants.CSS_PROFILE_SAVE_BUTTON);
        saveChangesButton.addClickListener(e -> saveChanges());
        
        // Add all sections to form layout
        formLayout.add(emailSection, nameSection, accountInfoSection, saveChangesButton);
        
        return formLayout;
    }
    
    private void saveChanges() {
        LoggingUtil.debug(LOG, "saveChanges", "Saving profile changes for user: %s", 
                currentUser != null ? currentUser.getUsername() : "unknown");
        
        if (currentUser != null) {
            try {
                // Update the user's name
                currentUser.setUsername(fullNameField.getValue());
                userService.save(currentUser);
                
                // Show success notification
                Notification success = Notification.show("Profile updated successfully");
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                success.setPosition(Notification.Position.TOP_CENTER);
                success.setDuration(3000);
                
                LoggingUtil.info(LOG, "saveChanges", "Profile updated successfully for user: %s", currentUser.getId());
            } catch (Exception e) {
                // Show error notification
                Notification error = Notification.show("Error updating profile: " + e.getMessage());
                error.addThemeVariants(NotificationVariant.LUMO_ERROR);
                error.setPosition(Notification.Position.TOP_CENTER);
                error.setDuration(5000);
                
                LoggingUtil.error(LOG, "saveChanges", "Error updating profile", e);
            }
        } else {
            LoggingUtil.error(LOG, "saveChanges", "Cannot save changes: currentUser is null");
            
            // Show error notification
            Notification error = Notification.show("Error: Cannot identify current user");
            error.addThemeVariants(NotificationVariant.LUMO_ERROR);
            error.setPosition(Notification.Position.TOP_CENTER);
            error.setDuration(5000);
        }
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting authentication");
        
        // Get authentication 
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoggingUtil.debug(LOG, "beforeEnter", "Authentication: %s", auth != null ? 
                auth.getName() + " (authenticated: " + auth.isAuthenticated() + ")" : "null");
        
        // Check if user is anonymous or lacks required role - @Secured isn't enough since anonymous users are "authenticated"
        boolean hasAccess = false;
        if (auth != null && auth.getAuthorities() != null) {
            hasAccess = auth.getAuthorities().stream()
                    .anyMatch(grantedAuth -> "ROLE_USER".equals(grantedAuth.getAuthority()));
        }
        
        // Redirect to login if not authorized
        if (!hasAccess) {
            LoggingUtil.info(LOG, "beforeEnter", "User lacks required role, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN); 
            return;
        }
        
        // Try to get the current user - this is needed for the view to function properly
        try {
            if (auth != null) {
                currentUser = userService.findByUsername(auth.getName()).orElse(null);
                if (currentUser != null) {
                    LoggingUtil.info(LOG, "beforeEnter", "Current user loaded: %s (ID: %s)", 
                        currentUser.getUsername(), currentUser.getId());
                } else {
                    LoggingUtil.warn(LOG, "beforeEnter", "Could not find user with username: %s", auth.getName());
                    // If we can't find the user in the database, redirect to login
                    event.forwardTo(UIConstants.ROUTE_LOGIN);
                    return;
                }
            } else {
                LoggingUtil.warn(LOG, "beforeEnter", "Authentication is null, cannot load current user");
                event.forwardTo(UIConstants.ROUTE_LOGIN);
                return;
            }
            
            // Initialize the view only when user is authenticated and authorized
            initializeView();
            
        } catch (Exception e) {
            // Log error and redirect to login
            LOG.error("Error in beforeEnter: {}", e.getMessage(), e);
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
