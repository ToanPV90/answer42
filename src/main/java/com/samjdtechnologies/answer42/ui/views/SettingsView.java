package com.samjdtechnologies.answer42.ui.views;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.model.UserPreferences;
import com.samjdtechnologies.answer42.service.UserPreferencesService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * SettingsView displays user settings and preferences.
 */
@Route(value = UIConstants.ROUTE_SETTINGS, layout = MainLayout.class)
@PageTitle("Answer42 - Settings")
@Secured("ROLE_USER")
public class SettingsView extends Div implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsView.class);
    
    private final UserService userService;
    private final UserPreferencesService userPreferencesService;
    private User currentUser;
    
    
    private ComboBox<String> academicFieldComboBox;
    
    private Checkbox studyMaterialGenerationToggle;
    private Checkbox emailNotificationsToggle;
    private Checkbox systemNotificationsToggle;

    /**
     * Constructs the settings view with necessary service dependencies.
     * Initializes the view with user-specific settings and preferences.
     * 
     * @param userService the service for user data operations
     * @param userPreferencesService the service for user preferences operations
     */
    public SettingsView(UserService userService, UserPreferencesService userPreferencesService) {
        this.userService = userService;
        this.userPreferencesService = userPreferencesService;
        
        addClassName(UIConstants.CSS_SETTINGS_VIEW);
        getStyle().setHeight("auto");

        LoggingUtil.debug(LOG, "SettingsView", "SettingsView initialized");
    }
    
    private void initializeView() {
        LoggingUtil.debug(LOG, "initializeView", "Initializing view components");
        
        // Configure the view
        removeAll();
        
        // Add all components to the view
        add(createWelcomeSection(), 
            createPreferencesSection(), 
            createDangerZoneSection()
        );

        LoggingUtil.debug(LOG, "initializeView", "Settings view components initialized");
    }

    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("My Settings");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Customize Your Experience and Manage Account Preferences");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    
    private Div createPreferencesSection() {
        Div preferencesSection = new Div();
        preferencesSection.addClassName(UIConstants.CSS_SETTINGS_SECTION);
        
        H3 sectionTitle = new H3("Preferences");
        
        // Study Material Generation toggle
        Div studyMaterialOption = new Div();
        studyMaterialOption.addClassName(UIConstants.CSS_SETTING_OPTION);
        
        Div studyMaterialDescription = new Div();
        studyMaterialDescription.addClassName(UIConstants.CSS_OPTION_DESCRIPTION);
        
        Span studyMaterialTitle = new Span("Study Material Generation");
        studyMaterialTitle.addClassName(UIConstants.CSS_OPTION_TITLE);
        
        Span studyMaterialDetail = new Span("Automatically generate study materials when uploading papers. " +
                "When enabled, the system will automatically generate flashcards, practice questions, and concept maps for each paper you upload.");
        studyMaterialDetail.addClassName(UIConstants.CSS_OPTION_DETAIL);
        
        studyMaterialDescription.add(studyMaterialTitle, studyMaterialDetail);
        
        Div studyMaterialControl = new Div();
        studyMaterialControl.addClassName(UIConstants.CSS_SETTING_CONTROL);
        
        studyMaterialGenerationToggle = new Checkbox("Enable");
        studyMaterialGenerationToggle.setValue(false);
        
        studyMaterialControl.add(studyMaterialGenerationToggle);
        
        studyMaterialOption.add(studyMaterialDescription, studyMaterialControl);
        
        // Email Notifications toggle
        Div emailNotificationsOption = new Div();
        emailNotificationsOption.addClassName(UIConstants.CSS_SETTING_OPTION);
        
        Div emailNotificationsDescription = new Div();
        emailNotificationsDescription.addClassName(UIConstants.CSS_OPTION_DESCRIPTION);
        
        Span emailNotificationsTitle = new Span("Email Notifications");
        emailNotificationsTitle.addClassName(UIConstants.CSS_OPTION_TITLE);
        
        Span emailNotificationsDetail = new Span("Receive emails about activity and updates");
        emailNotificationsDetail.addClassName(UIConstants.CSS_OPTION_DETAIL);
        
        emailNotificationsDescription.add(emailNotificationsTitle, emailNotificationsDetail);
        
        Div emailNotificationsControl = new Div();
        emailNotificationsControl.addClassName(UIConstants.CSS_SETTING_CONTROL);
        
        emailNotificationsToggle = new Checkbox("Enable");
        emailNotificationsToggle.setValue(true);
        
        emailNotificationsControl.add(emailNotificationsToggle);
        
        emailNotificationsOption.add(emailNotificationsDescription, emailNotificationsControl);
        
        // System Notifications toggle
        Div systemNotificationsOption = new Div();
        systemNotificationsOption.addClassName(UIConstants.CSS_SETTING_OPTION);
        
        Div systemNotificationsDescription = new Div();
        systemNotificationsDescription.addClassName(UIConstants.CSS_OPTION_DESCRIPTION);
        
        Span systemNotificationsTitle = new Span("System Notifications");
        systemNotificationsTitle.addClassName(UIConstants.CSS_OPTION_TITLE);
        
        Span systemNotificationsDetail = new Span("Show notifications in the application");
        systemNotificationsDetail.addClassName(UIConstants.CSS_OPTION_DETAIL);
        
        systemNotificationsDescription.add(systemNotificationsTitle, systemNotificationsDetail);
        
        Div systemNotificationsControl = new Div();
        systemNotificationsControl.addClassName(UIConstants.CSS_SETTING_CONTROL);
        
        systemNotificationsToggle = new Checkbox("Enable");
        systemNotificationsToggle.setValue(true);
        
        systemNotificationsControl.add(systemNotificationsToggle);
        
        systemNotificationsOption.add(systemNotificationsDescription, systemNotificationsControl);
        
        preferencesSection.add(sectionTitle, studyMaterialOption, emailNotificationsOption, systemNotificationsOption);
        
        return preferencesSection;
    }
    
    private Div createDangerZoneSection() {
        Div dangerZoneSection = new Div();
        dangerZoneSection.addClassName(UIConstants.CSS_SETTINGS_SECTION);
        dangerZoneSection.addClassName(UIConstants.CSS_DANGER_ZONE);
        
        H3 sectionTitle = new H3("Danger Zone");
        
        Paragraph warningText = new Paragraph("Once you delete your account, there's no going back. Please be certain.");
        
        Button deleteAccountButton = new Button("Delete Account");
        deleteAccountButton.addClassName(UIConstants.CSS_DELETE_ACCOUNT_BUTTON);
        deleteAccountButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteAccountButton.addClickListener(e -> confirmDeleteAccount());
        
        dangerZoneSection.add(sectionTitle, warningText, deleteAccountButton);
        
        return dangerZoneSection;
    }
    
    
    
    private void confirmDeleteAccount() {
        LoggingUtil.debug(LOG, "confirmDeleteAccount", "Showing delete account confirmation dialog");
        
        Dialog confirmDialog = new Dialog();
        confirmDialog.setCloseOnEsc(true);
        confirmDialog.setCloseOnOutsideClick(false);
        
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);
        
        H3 dialogTitle = new H3("Confirm Account Deletion");
        
        Paragraph warningMessage = new Paragraph("This action cannot be undone. All of your data, including papers, projects, and chat history will be permanently deleted.");
        
        Checkbox confirmCheckbox = new Checkbox("I understand that this action is permanent");
        
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();
        buttonLayout.setSpacing(true);
        
        Button cancelButton = new Button("Cancel", e -> confirmDialog.close());
        
        Button deleteButton = new Button("Delete My Account");
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        deleteButton.setEnabled(false);
        
        confirmCheckbox.addValueChangeListener(e -> deleteButton.setEnabled(e.getValue()));
        
        deleteButton.addClickListener(e -> {
            if (currentUser != null) {
                try {
                    UUID userId = currentUser.getId();
                    
                    // Delete user preferences first (due to foreign key constraint)
                    LoggingUtil.info(LOG, "confirmDeleteAccount", "Deleting user preferences for user ID: %s", userId);
                    userPreferencesService.deleteByUserId(userId);
                    
                    // Delete the user from the database
                    LoggingUtil.info(LOG, "confirmDeleteAccount", "Deleting user account: %s", userId);
                    userService.deleteById(userId);
                    
                    confirmDialog.close();
                    
                    Notification success = Notification.show("Your account has been deleted successfully");
                    success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    success.setPosition(Notification.Position.TOP_CENTER);
                    success.setDuration(3000);
                    
                    // Redirect to login page after a short delay
                    UI.getCurrent().getPage().executeJs(
                            "setTimeout(function() { window.location.href = 'login'; }, 3000);");
                    
                } catch (Exception ex) {
                    LoggingUtil.error(LOG, "confirmDeleteAccount", "Error deleting account", ex);
                    
                    confirmDialog.close();
                    
                    Notification error = Notification.show("Error deleting account: " + ex.getMessage());
                    error.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    error.setPosition(Notification.Position.TOP_CENTER);
                    error.setDuration(5000);
                }
            }
        });
        
        buttonLayout.add(cancelButton, deleteButton);
        
        dialogLayout.add(dialogTitle, warningMessage, confirmCheckbox, buttonLayout);
        
        confirmDialog.add(dialogLayout);
        confirmDialog.open();
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
        // Get the current user from the session (stored by MainLayout)
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "beforeEnter", "Retrieved user from session: %s (ID: %s)", 
                currentUser.getUsername(), currentUser.getId());
            
            // Load user preferences
            try {
                UserPreferences preferences = userPreferencesService.getByUserId(currentUser.getId());
                
                // Initialize the view with the user's data
                initializeView();
                
                // Set the form values based on saved preferences
                if (preferences != null) {
                    LoggingUtil.debug(LOG, "beforeEnter", "Loading saved preferences for user ID: %s", currentUser.getId());
                    
                    // Set academic field if available
                    if (preferences.getAcademicField() != null && !preferences.getAcademicField().isEmpty()) {
                        academicFieldComboBox.setValue(preferences.getAcademicField());
                    }
                    
                    // Set toggle values
                    studyMaterialGenerationToggle.setValue(preferences.isStudyMaterialGenerationEnabled());
                    emailNotificationsToggle.setValue(preferences.isEmailNotificationsEnabled());
                    systemNotificationsToggle.setValue(preferences.isSystemNotificationsEnabled());
                    
                    LoggingUtil.debug(LOG, "beforeEnter", "User preferences loaded successfully");
                }
            } catch (Exception e) {
                LoggingUtil.error(LOG, "beforeEnter", "Error loading user preferences", e);
                // We still show the view, just with default values
                initializeView();
            }
        } else {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
