package com.samjdtechnologies.answer42.ui.helpers.views;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samjdtechnologies.answer42.model.db.Subscription;
import com.samjdtechnologies.answer42.model.db.SubscriptionPlan;
import com.samjdtechnologies.answer42.model.db.User;
import com.samjdtechnologies.answer42.service.SubscriptionService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Helper class for processing subscription payments and creating subscriptions.
 */
public class SubscriptionViewHelper {
    
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionViewHelper.class);
    
    public enum PaymentMethod {
        CREDIT_CARD("Credit Card"),
        BITCOIN("Bitcoin (+20% discount)"),
        PAYPAL("PayPal");
        
        private final String displayName;
        
        PaymentMethod(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private final SubscriptionService subscriptionService;
    private User currentUser;
    private SubscriptionPlan selectedPlan;
    private boolean isYearly;
    private PaymentMethod paymentMethod;
    private Consumer<Subscription> successCallback;
    
    /**
     * Constructs a new SubscriptionProcessor with the necessary dependencies.
     * 
     * @param subscriptionService the service for subscription operations
     * @param currentUser the current user
     */
    public SubscriptionViewHelper(SubscriptionService subscriptionService, User currentUser) {
        this.subscriptionService = subscriptionService;
        this.currentUser = currentUser;
        this.paymentMethod = PaymentMethod.CREDIT_CARD;
    }
    
    /**
     * Shows the payment dialog for the selected plan.
     * 
     * @param plan the selected subscription plan
     * @param isYearly whether the subscription is yearly
     * @param onSuccess callback to execute on successful subscription
     */
    public void showPaymentDialog(SubscriptionPlan plan, boolean isYearly, Consumer<Subscription> onSuccess) {
        LoggingUtil.debug(LOG, "showPaymentDialog", "Showing payment dialog for plan: %s", plan.getName());
        
        this.selectedPlan = plan;
        this.isYearly = isYearly;
        this.successCallback = onSuccess;
        
        Dialog dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setDraggable(false);
        dialog.setResizable(false);
        dialog.setWidth("600px");
        
        dialog.add(createPaymentDialog(dialog));
        
        dialog.open();
    }
    
    /**
     * Creates the payment dialog content.
     * 
     * @param dialog the dialog to close on completion
     * @return the payment dialog content
     */
    private VerticalLayout createPaymentDialog(Dialog dialog) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        
        // Get subscription terms
        String periodText = isYearly ? "year" : "month";
        String priceText = isYearly ? 
            "$" + selectedPlan.getPriceAnnually() + "/year" : 
            "$" + selectedPlan.getPriceMonthly() + "/month";
        
        // Header with plan info
        Div header = new Div();
        header.addClassNames(UIConstants.CSS_PAYMENT_DIALOG_HEADER);
        
        H3 title = new H3("Subscribe to " + selectedPlan.getName() + " Plan");
        
        Paragraph description = new Paragraph(
            "You are subscribing to the " + selectedPlan.getName() + " plan for " + 
            priceText + ". You'll be charged immediately and again on the same day each " + 
            periodText + " until you cancel."
        );
        
        // Add a close button to the corner
        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.getElement().getStyle().set("position", "absolute");
        closeButton.getElement().getStyle().set("right", "0");
        closeButton.getElement().getStyle().set("top", "0");
        closeButton.getElement().getStyle().set("margin", "var(--lumo-space-m)");
        closeButton.addClickListener(e -> dialog.close());
        
        header.add(title, description, closeButton);
        
        // Payment method selector
        RadioButtonGroup<PaymentMethod> paymentMethodGroup = new RadioButtonGroup<>();
        paymentMethodGroup.setLabel("Payment Method");
        paymentMethodGroup.setItems(PaymentMethod.values());
        paymentMethodGroup.setValue(PaymentMethod.CREDIT_CARD);
        paymentMethodGroup.addValueChangeListener(e -> {
            this.paymentMethod = e.getValue();
            content.remove(content.getComponentAt(2)); // Remove current payment form
            content.addComponentAtIndex(2, createPaymentForm(this.paymentMethod, dialog));
        });
        paymentMethodGroup.addClassName(UIConstants.CSS_PAYMENT_METHOD_SELECTOR);
        
        // Initial payment form (credit card by default)
        VerticalLayout paymentForm = createPaymentForm(PaymentMethod.CREDIT_CARD, dialog);
        
        content.add(header, paymentMethodGroup, paymentForm);
        return content;
    }
    
    /**
     * Creates the payment form based on the selected payment method.
     * 
     * @param method the selected payment method
     * @param dialog the dialog to close on completion
     * @return the payment form for the selected method
     */
    private VerticalLayout createPaymentForm(PaymentMethod method, Dialog dialog) {
        VerticalLayout form = new VerticalLayout();
        form.setPadding(true);
        form.setSpacing(true);
        
        switch (method) {
            case CREDIT_CARD:
                form.add(createCreditCardForm(dialog));
                break;
                
            case BITCOIN:
                form.add(createBitcoinForm(dialog));
                break;
                
            case PAYPAL:
                form.add(createPayPalForm(dialog));
                break;
                
            default:
                form.add(new Paragraph("Payment method not implemented"));
                break;
        }
        
        return form;
    }
    
    /**
     * Creates the credit card payment form.
     * 
     * @param dialog the dialog to close on completion
     * @return the credit card payment form
     */
    private FormLayout createCreditCardForm(Dialog dialog) {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );
        
        TextField cardName = new TextField("Cardholder Name");
        cardName.setPlaceholder("John Smith");
        cardName.setRequired(true);
        cardName.setWidthFull();
        
        // Custom credit card number field with validation
        TextField cardNumber = new TextField("Card Number");
        cardNumber.setPlaceholder("•••• •••• •••• ••••");
        cardNumber.setRequired(true);
        cardNumber.setWidthFull();
        cardNumber.setPattern("[0-9\\s]{13,19}");
        cardNumber.setMaxLength(19);
        
        // Create a horizontal layout for expiry date and CVV
        HorizontalLayout cardDetails = new HorizontalLayout();
        cardDetails.setWidthFull();
        cardDetails.setSpacing(true);
        
        ComboBox<String> expiryMonth = new ComboBox<>("Expiry Month");
        expiryMonth.setItems("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
        expiryMonth.setPlaceholder("MM");
        expiryMonth.setRequired(true);
        expiryMonth.setWidth("100px");
        
        ComboBox<String> expiryYear = new ComboBox<>("Expiry Year");
        // Add years starting from current year
        int currentYear = ZonedDateTime.now().getYear();
        String[] years = new String[10];
        for (int i = 0; i < 10; i++) {
            years[i] = String.valueOf(currentYear + i);
        }
        expiryYear.setItems(years);
        expiryYear.setPlaceholder("YYYY");
        expiryYear.setRequired(true);
        expiryYear.setWidth("120px");
        
        TextField cvv = new TextField("Security Code (CVV)");
        cvv.setPlaceholder("•••");
        cvv.setRequired(true);
        cvv.setPattern("[0-9]{3,4}");
        cvv.setMaxLength(4);
        cvv.setWidth("100px");
        
        cardDetails.add(expiryMonth, expiryYear, cvv);
        
        // Billing address
        TextField billingAddress = new TextField("Billing Address");
        billingAddress.setPlaceholder("123 Main St");
        billingAddress.setRequired(true);
        billingAddress.setWidthFull();
        
        TextField city = new TextField("City");
        city.setPlaceholder("New York");
        city.setRequired(true);
        
        TextField zipCode = new TextField("Zip/Postal Code");
        zipCode.setPlaceholder("10001");
        zipCode.setRequired(true);
        
        // Country dropdown
        ComboBox<String> country = new ComboBox<>("Country");
        country.setItems("United States", "Canada", "United Kingdom", "Australia", "Germany", "France", "Japan");
        country.setValue("United States");
        country.setRequired(true);
        
        // Submit button
        Button submitButton = new Button("Subscribe Now", event -> {
            try {
                processCreditCardPayment(dialog);
            } catch (Exception e) {
                LoggingUtil.error(LOG, "createCreditCardForm", "Error processing credit card payment: %s", e.getMessage());
                Notification.show("Payment failed: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.setWidthFull();
        
        // Cancel button
        Button cancelButton = new Button("Cancel", event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.setWidthFull();
        
        HorizontalLayout buttons = new HorizontalLayout(cancelButton, submitButton);
        buttons.setWidthFull();
        
        // Add components to the form
        form.add(cardName, cardNumber);
        form.add(cardDetails, 2); // Span across 2 columns
        form.add(billingAddress, 2); // Span across 2 columns
        form.add(city, zipCode);
        form.add(country, 2); // Span across 2 columns
        form.add(buttons, 2); // Span across 2 columns
        
        return form;
    }
    
    /**
     * Creates the Bitcoin payment form.
     * 
     * @param dialog the dialog to close on completion
     * @return the Bitcoin payment form
     */
    private VerticalLayout createBitcoinForm(Dialog dialog) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        
        // Bitcoin payment message
        Div messageBox = new Div();
        messageBox.addClassName(UIConstants.CSS_BITCOIN_PAYMENT_MESSAGE);
        
        Icon infoIcon = VaadinIcon.INFO_CIRCLE.create();
        infoIcon.setColor("#F7931A"); /* Official Bitcoin orange */
        
        Span discountLabel = new Span("20% Discount Applied!");
        discountLabel.getStyle().set("font-weight", "bold");
        discountLabel.getStyle().set("color", "#F7931A"); /* Official Bitcoin orange */
        
        Paragraph message = new Paragraph(
            "Pay with Bitcoin and receive a 20% discount on your subscription! " +
            "Your total with the discount will be: "
        );
        
        // Calculate discounted price
        String originalPrice = isYearly ? 
            "$" + selectedPlan.getPriceAnnually() : 
            "$" + selectedPlan.getPriceMonthly();
            
        // Apply 20% discount for Bitcoin
        String discountedPrice = isYearly ? 
            "$" + selectedPlan.getPriceAnnually().multiply(new java.math.BigDecimal("0.8")) : 
            "$" + selectedPlan.getPriceMonthly().multiply(new java.math.BigDecimal("0.8"));
        
        Span priceInfo = new Span(originalPrice + " → " + discountedPrice);
        priceInfo.getStyle().set("font-weight", "bold");
        
        HorizontalLayout messageHeader = new HorizontalLayout(infoIcon, discountLabel);
        messageHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        
        messageBox.add(messageHeader, message, priceInfo);
        
        // Mock QR code and Bitcoin address
        Div qrCodeSection = new Div();
        qrCodeSection.addClassName(UIConstants.CSS_BITCOIN_QR_SECTION);
        
        // Create a QR code placeholder with Vaadin components
        Div qrCode = new Div();
        qrCode.getStyle()
            .set("width", "200px")
            .set("height", "200px")
            .set("background-color", "white")
            .set("border", "1px solid black")
            .set("position", "relative")
            .set("margin", "0 auto");
            
        // Add some QR-like patterns
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if ((i + j) % 2 == 0 || (i == 0 && j == 0) || (i == 0 && j == 4) || (i == 4 && j == 0)) {
                    Div module = new Div();
                    module.getStyle()
                        .set("position", "absolute")
                        .set("width", "20px")
                        .set("height", "20px")
                        .set("background-color", "black")
                        .set("top", (i * 40 + 10) + "px")
                        .set("left", (j * 40 + 10) + "px");
                    qrCode.add(module);
                }
            }
        }
        
        // Add Bitcoin logo in center
        Icon bitcoinIcon = VaadinIcon.COIN_PILES.create();
        bitcoinIcon.getStyle()
            .set("position", "absolute")
            .set("top", "50%")
            .set("left", "50%")
            .set("transform", "translate(-50%, -50%)")
            .set("color", "#F7931A") /* Official Bitcoin orange */
            .set("width", "40px")
            .set("height", "40px");
        qrCode.add(bitcoinIcon);
        
        // Mock BTC address
        Div addressBox = new Div();
        addressBox.addClassName(UIConstants.CSS_BITCOIN_ADDRESS_BOX);
        
        Span addressLabel = new Span("Bitcoin Address");
        addressLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");
        addressLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
        
        Span address = new Span("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        address.getStyle().set("font-family", "monospace");
        address.getStyle().set("word-break", "break-all");
        
        Button copyButton = new Button("Copy", new Icon(VaadinIcon.COPY));
        copyButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        copyButton.setWidth("100%");
        copyButton.addClickListener(e -> {
            // In a real implementation, this would copy to clipboard
            Notification.show("Address copied to clipboard", 2000, Notification.Position.BOTTOM_START);
        });
        
        addressBox.add(addressLabel, address, copyButton);
        
        VerticalLayout qrLayout = new VerticalLayout(qrCode, addressBox);
        qrLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        qrLayout.setPadding(false);
        
        qrCodeSection.add(qrLayout);
        
        // Instructions
        Div instructionsBox = new Div();
        instructionsBox.addClassName(UIConstants.CSS_BITCOIN_INSTRUCTIONS);
        
        H3 instructionsTitle = new H3("Instructions");
        
        VerticalLayout steps = new VerticalLayout();
        steps.setPadding(false);
        steps.setSpacing(false);
        
        steps.add(createStep(1, "Scan the QR code or copy the Bitcoin address."));
        steps.add(createStep(2, "Send the exact amount shown above from your Bitcoin wallet."));
        steps.add(createStep(3, "After payment confirmation (typically 1-3 network confirmations), your subscription will be activated."));
        steps.add(createStep(4, "You'll receive a confirmation email with your subscription details."));
        
        instructionsBox.add(instructionsTitle, steps);
        
        // Buttons
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        
        // For demo purposes, this button will simulate a successful Bitcoin payment
        Button confirmButton = new Button("I've Sent the Payment", e -> {
            processBitcoinPayment(dialog);
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        buttons.add(cancelButton, confirmButton);
        buttons.setFlexGrow(1, cancelButton, confirmButton);
        
        layout.add(messageBox, qrCodeSection, instructionsBox, buttons);
        return layout;
    }
    
    /**
     * Creates a numbered step for instructions.
     * 
     * @param number the step number
     * @param text the step text
     * @return a layout with the numbered step
     */
    private HorizontalLayout createStep(int number, String text) {
        HorizontalLayout step = new HorizontalLayout();
        step.setSpacing(true);
        step.setAlignItems(FlexComponent.Alignment.START);
        
        Div circle = new Div();
        circle.setText(String.valueOf(number));
        circle.getStyle().set("background-color", "var(--lumo-primary-color)");
        circle.getStyle().set("color", "var(--lumo-primary-contrast-color)");
        circle.getStyle().set("border-radius", "50%");
        circle.getStyle().set("width", "24px");
        circle.getStyle().set("height", "24px");
        circle.getStyle().set("display", "flex");
        circle.getStyle().set("align-items", "center");
        circle.getStyle().set("justify-content", "center");
        circle.getStyle().set("font-size", "var(--lumo-font-size-s)");
        circle.getStyle().set("font-weight", "bold");
        circle.getStyle().set("flex-shrink", "0");
        
        Paragraph description = new Paragraph(text);
        description.getStyle().set("margin", "0");
        
        step.add(circle, description);
        return step;
    }
    
    /**
     * Creates the PayPal payment form.
     * 
     * @param dialog the dialog to close on completion
     * @return the PayPal payment form
     */
    private VerticalLayout createPayPalForm(Dialog dialog) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        
        Div paypalInfo = new Div();
        paypalInfo.addClassName(UIConstants.CSS_PAYPAL_INFO);
        
        // Create a PayPal logo placeholder with Vaadin components
        Div paypalLogo = new Div();
        paypalLogo.getStyle()
            .set("width", "120px")
            .set("height", "40px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background-color", "#0070BA")
            .set("border-radius", "4px")
            .set("color", "white")
            .set("font-weight", "bold")
            .set("font-size", "18px");
        paypalLogo.setText("PayPal");
        
        Paragraph info = new Paragraph(
            "You'll be redirected to PayPal to complete your payment securely. " +
            "After completing the payment, you'll be automatically returned to Answer42."
        );
        
        String priceText = isYearly ? 
            "$" + selectedPlan.getPriceAnnually() + "/year" : 
            "$" + selectedPlan.getPriceMonthly() + "/month";
            
        Span priceInfo = new Span("Total: " + priceText);
        priceInfo.getStyle().set("font-weight", "bold");
        
        paypalInfo.add(paypalLogo, info, priceInfo);
        
        // Email field
        EmailField emailField = new EmailField("PayPal Email (Optional)");
        emailField.setPlaceholder("your-email@example.com");
        emailField.setHelperText("If different from your account email");
        emailField.setWidthFull();
        
        // Note field
        TextArea noteField = new TextArea("Note (Optional)");
        noteField.setPlaceholder("Additional information for this subscription");
        noteField.setWidthFull();
        noteField.setMaxHeight("100px");
        
        // Buttons
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        
        Button payWithPaypalButton = new Button("Continue to PayPal", e -> {
            processPayPalPayment(dialog);
        });
        payWithPaypalButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        buttons.add(cancelButton, payWithPaypalButton);
        buttons.setFlexGrow(1, cancelButton, payWithPaypalButton);
        
        layout.add(paypalInfo, emailField, noteField, buttons);
        return layout;
    }
    
    /**
     * Processes a credit card payment.
     * 
     * @param dialog the dialog to close on completion
     */
    private void processCreditCardPayment(Dialog dialog) {
        LoggingUtil.info(LOG, "processCreditCardPayment", 
            "Processing credit card payment for user: %s, plan: %s", 
            currentUser.getUsername(), selectedPlan.getName());
        
        // In a real implementation, this would integrate with a payment processor
        // For now, we'll simulate a successful payment
        
        // Mock payment provider transaction ID
        String transactionId = "cc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        
        // Create the subscription
        createSubscription("credit_card", transactionId, dialog);
    }
    
    /**
     * Processes a Bitcoin payment.
     * 
     * @param dialog the dialog to close on completion
     */
    private void processBitcoinPayment(Dialog dialog) {
        LoggingUtil.info(LOG, "processBitcoinPayment", 
            "Processing Bitcoin payment for user: %s, plan: %s", 
            currentUser.getUsername(), selectedPlan.getName());
        
        // In a real implementation, this would integrate with a Bitcoin payment processor
        // For now, we'll simulate a successful payment
        
        // Mock payment provider transaction ID
        String transactionId = "btc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        
        // Create the subscription
        createSubscription("bitcoin", transactionId, dialog);
    }
    
    /**
     * Processes a PayPal payment.
     * 
     * @param dialog the dialog to close on completion
     */
    private void processPayPalPayment(Dialog dialog) {
        LoggingUtil.info(LOG, "processPayPalPayment", 
            "Processing PayPal payment for user: %s, plan: %s", 
            currentUser.getUsername(), selectedPlan.getName());
        
        // In a real implementation, this would integrate with PayPal
        // For now, we'll simulate a successful payment
        
        // Mock payment provider transaction ID
        String transactionId = "pp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        
        // Create the subscription
        createSubscription("paypal", transactionId, dialog);
    }
    
    /**
     * Creates a subscription record after successful payment.
     * 
     * @param paymentProvider the payment provider (credit_card, bitcoin, paypal)
     * @param transactionId the payment provider's transaction ID
     * @param dialog the dialog to close on completion
     */
    private void createSubscription(String paymentProvider, String transactionId, Dialog dialog) {
        LoggingUtil.info(LOG, "createSubscription", 
            "Creating subscription for user: %s, plan: %s, payment: %s, transaction: %s", 
            currentUser.getUsername(), selectedPlan.getName(), paymentProvider, transactionId);
        
        try {
            // Create the subscription
            Subscription subscription = subscriptionService.createSubscription(
                currentUser.getId(), 
                selectedPlan.getId(), 
                paymentProvider, 
                transactionId, 
                isYearly
            );
            
            // Show success message
            showSuccessDialog(subscription, dialog);
            
            // Invoke the success callback if provided
            if (successCallback != null) {
                successCallback.accept(subscription);
            }
            
            LoggingUtil.info(LOG, "createSubscription", 
                "Subscription created successfully: %s", subscription.getId());
                
        } catch (Exception e) {
            LoggingUtil.error(LOG, "createSubscription", 
                "Error creating subscription: %s", e.getMessage());
                
            Notification notification = new Notification(
                "Failed to create subscription: " + e.getMessage(), 
                5000, 
                Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
        }
    }
    
    /**
     * Shows a success dialog after successful subscription.
     * 
     * @param subscription the created subscription
     * @param previousDialog the previous dialog to close
     */
    private void showSuccessDialog(Subscription subscription, Dialog previousDialog) {
        // Close the payment dialog
        previousDialog.close();
        
        // Create success dialog
        Dialog dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("500px");
        
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        
        Icon successIcon = VaadinIcon.CHECK_CIRCLE.create();
        successIcon.setColor("var(--lumo-success-color)");
        successIcon.setSize("64px");
        
        H3 title = new H3("Subscription Successful!");
        title.getStyle().set("margin-top", "0");
        
        String planType = subscription.getPlan().getName();
        
        Paragraph message = new Paragraph(
            "Thank you for subscribing to the " + planType + " plan! Your subscription is now active. " +
            "You have full access to all features included in your plan."
        );
        message.getStyle().set("text-align", "center");
        
        // Next steps list with HTML
        Div nextSteps = new Div();
        nextSteps.addClassName(UIConstants.CSS_SUBSCRIPTION_NEXT_STEPS);
        
        H3 nextStepsTitle = new H3("Next Steps");
        nextStepsTitle.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        Html nextStepsList = new Html(
            "<ul>" +
            "<li>Explore your new features and higher credit limits</li>" +
            "<li>Upload more papers to your projects</li>" +
            "<li>Try out the advanced AI analysis capabilities</li>" +
            "</ul>"
        );
        
        nextSteps.add(nextStepsTitle, nextStepsList);
        
        Button closeButton = new Button("Got It", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        content.add(successIcon, title, message, nextSteps, closeButton);
        dialog.add(content);
        
        dialog.open();
    }
}
