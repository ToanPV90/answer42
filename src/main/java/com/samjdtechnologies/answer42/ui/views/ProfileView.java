package com.samjdtechnologies.answer42.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.model.db.UserPreferences;
import com.samjdtechnologies.answer42.service.UserPreferencesService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
    private final UserPreferencesService userPreferencesService;
    private User currentUser;
    
    private TextField fullNameField;
    private Button saveChangesButton;
    private TextField emailField;
    private ComboBox<String> academicFieldComboBox;
    private Checkbox studyMaterialGenerationToggle;
    private Checkbox emailNotificationsToggle;
    private Checkbox systemNotificationsToggle;

    /**
     * Constructs the profile view with necessary service dependencies.
     * Initializes the view for displaying and managing user profile information.
     * 
     * @param userService the service for user-related operations including profile updates
     * @param userPreferencesService the service for user preferences operations
     */
    public ProfileView(UserService userService, UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
        this.userService = userService;
        
        addClassName(UIConstants.CSS_PROFILE_VIEW);
        getStyle().setHeight("auto");

        LoggingUtil.debug(LOG, "ProfileView", "ProfileView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing profile view components.");

        // Configure the view
        removeAll();
        
        // Add all components to the view
        add(createWelcomeSection(), createAccountInfoSection(), createUserProfileSection());
        
        LoggingUtil.debug(LOG, "initializeView", "Profile view components initialized");
    }

    private Div createUserProfileSection() {
        Div userProfileSection = new Div();
        userProfileSection.addClassName(UIConstants.CSS_SETTINGS_SECTION);
        
        H3 sectionTitle = new H3("User Profile");
        
        // Full Name field
        this.fullNameField = new TextField("Full Name");
        this.fullNameField.setValue(currentUser != null ? currentUser.getUsername() : "");
        this.fullNameField.setWidthFull();
        
        // Email field (read-only)
        this.emailField = new TextField("Email");
        this.emailField.setValue(currentUser != null ? currentUser.getEmail() : "");
        this.emailField.setReadOnly(true);
        this.emailField.setWidthFull();
        
        Span emailHelpText = new Span("Email cannot be changed. Contact support if you need to update your email.");
        emailHelpText.addClassName(UIConstants.CSS_HELP_TEXT);
        
        // Academic Field dropdown
        this.academicFieldComboBox = new ComboBox<>("Academic Field");
        this.academicFieldComboBox.setItems("Select field", "Computer Science", "Engineering", "Mathematics", "Physics", 
                "Chemistry", "Biology", "Medicine", "Psychology", "Sociology", "Economics", 
                "Business", "Law", "Humanities", "Arts", "Education", "Other");
        this.academicFieldComboBox.setValue("Select field");
        this.academicFieldComboBox.setWidthFull();
        
        // Save changes button
        this.saveChangesButton = new Button("Save Changes");
        this.saveChangesButton.addClassName(UIConstants.CSS_SAVE_BUTTON);
        this.saveChangesButton.addClickListener(e -> saveProfileChanges());
        
        userProfileSection.add(sectionTitle, fullNameField, emailField, emailHelpText, academicFieldComboBox, saveChangesButton);
        
        return userProfileSection;
    }

    private void saveProfileChanges() {
        LoggingUtil.debug(LOG, "saveProfileChanges", "Saving profile changes for user: %s", 
                currentUser != null ? currentUser.getUsername() : "unknown");
        
        if (currentUser != null) {
            try {
                // Update the user's name
                currentUser.setUsername(fullNameField.getValue());
                userService.save(currentUser);
                
                // Update user preferences
                LoggingUtil.debug(LOG, "saveProfileChanges", "Updating user preferences for user: %s", currentUser.getId());
                
                // Get or create user preferences
                UserPreferences preferences = userPreferencesService.getByUserId(currentUser.getId());
                
                // Update academic field
                preferences.setAcademicField(academicFieldComboBox.getValue());
                
                // Update notification preferences
                preferences.setStudyMaterialGenerationEnabled(studyMaterialGenerationToggle.getValue());
                preferences.setEmailNotificationsEnabled(emailNotificationsToggle.getValue());
                preferences.setSystemNotificationsEnabled(systemNotificationsToggle.getValue());
                
                // Save preferences
                userPreferencesService.save(preferences);
                
                // Show success notification
                Notification success = Notification.show("Settings updated successfully");
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                success.setPosition(Notification.Position.TOP_CENTER);
                success.setDuration(3000);
                
                LoggingUtil.info(LOG, "saveProfileChanges", "Settings updated successfully for user: %s", currentUser.getId());
            } catch (Exception e) {
                // Show error notification
                Notification error = Notification.show("Error updating settings: " + e.getMessage());
                error.addThemeVariants(NotificationVariant.LUMO_ERROR);
                error.setPosition(Notification.Position.TOP_CENTER);
                error.setDuration(5000);
                
                LoggingUtil.error(LOG, "saveProfileChanges", "Error updating settings", e);
            }
        } else {
            LoggingUtil.error(LOG, "saveProfileChanges", "Cannot save changes: currentUser is null");
            
            // Show error notification
            Notification error = Notification.show("Error: Cannot identify current user");
            error.addThemeVariants(NotificationVariant.LUMO_ERROR);
            error.setPosition(Notification.Position.TOP_CENTER);
            error.setDuration(5000);
        }
    }

    private Div createAccountInfoSection() {
        Div accountInfoSection = new Div();
        accountInfoSection.addClassName(UIConstants.CSS_SETTINGS_SECTION);
        
        H3 sectionTitle = new H3("Account Information");
        
        // Create a grid-like layout for the account information
        Div infoGrid = new Div();
        infoGrid.addClassName(UIConstants.CSS_ACCOUNT_INFO_GRID);
        
        // Account Type
        Div accountTypeSection = new Div();
        Span accountTypeLabel = new Span("Account Type");
        accountTypeLabel.addClassName(UIConstants.CSS_ACCOUNT_INFO_LABEL);
        
        Span accountTypeValue = new Span("Free");
        accountTypeValue.addClassName(UIConstants.CSS_ACCOUNT_INFO_VALUE);
        
        accountTypeSection.add(accountTypeLabel, accountTypeValue);
        
        // Member Since
        Div memberSinceSection = new Div();
        Span memberSinceLabel = new Span("Member Since");
        memberSinceLabel.addClassName(UIConstants.CSS_ACCOUNT_INFO_LABEL);
        
        // Get member since date from the user's createdAt field
        String joinDate = currentUser != null && currentUser.getCreatedAt() != null ? 
            currentUser.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")) : 
            ""; // Empty if not available
        
        Span memberSinceValue = new Span(joinDate);
        memberSinceValue.addClassName(UIConstants.CSS_ACCOUNT_INFO_VALUE);
        
        memberSinceSection.add(memberSinceLabel, memberSinceValue);
        
        // Last Login
        Div lastLoginSection = new Div();
        Span lastLoginLabel = new Span("Last Login");
        lastLoginLabel.addClassName(UIConstants.CSS_ACCOUNT_INFO_LABEL);
        lastLoginLabel.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        // Get last login date from the user's lastLogin field
        String lastLoginDate = currentUser != null && currentUser.getLastLogin() != null ? 
            currentUser.getLastLogin().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")) : 
            ""; // Empty if not available
        
        Span lastLoginValue = new Span(lastLoginDate);
        lastLoginValue.addClassName(UIConstants.CSS_ACCOUNT_INFO_VALUE);
        
        lastLoginSection.add(lastLoginLabel, lastLoginValue);
        
        // Add to grid layout
        infoGrid.add(accountTypeSection, memberSinceSection, lastLoginSection);
        
        accountInfoSection.add(sectionTitle, infoGrid);
        return accountInfoSection;
    }
    
    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("Your Profile");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Update your personal details and view account information");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
        // Get the current user from the session (stored by MainLayout)
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "beforeEnter", "Retrieved user from session: %s (ID: %s)", 
                currentUser.getUsername(), currentUser.getId());
            
            // Initialize the view with the user's data
            initializeView();
        } else {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
