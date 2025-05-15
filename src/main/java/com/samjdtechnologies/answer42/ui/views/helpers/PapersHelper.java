package com.samjdtechnologies.answer42.ui.views.helpers;

import com.samjdtechnologies.answer42.model.Paper;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.StreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class for PapersView to handle non-UI rendering logic
 */
public class PapersHelper {

    /**
     * Update list of papers and refresh the grid based on search and filter criteria
     */
    public static void updateList(User currentUser, 
                               String searchTerm, 
                               String statusValue,
                               int page,
                               int pageSize,
                               Grid<Paper> grid,
                               Span pageInfo,
                               Button prevButton, 
                               Button nextButton,
                               PaperService paperService,
                               Consumer<Integer> pageUpdateCallback) {
        
        // Use helper to fetch papers list
        Page<Paper> papers = fetchPapersList(
            currentUser, searchTerm, statusValue, page, pageSize, paperService);
        
        // Update grid
        grid.setItems(papers.getContent());
        
        // Update pagination and potentially adjust page if beyond bounds
        int adjustedPage = updatePagination(
            papers.getNumber(), papers.getTotalPages(), papers.getTotalElements(), 
            pageInfo, prevButton, nextButton);
        
        // If page changed, update the current page and refresh
        if (adjustedPage != page && pageUpdateCallback != null) {
            pageUpdateCallback.accept(adjustedPage);
        }
    }

    /**
     * Configure the grid with standard paper columns and behaviors
     */
    public static void configureGrid(Grid<Paper> grid, 
                                     ComponentRenderer<Component, Paper> actionsRenderer,
                                     Consumer<Paper> itemClickHandler) {
        grid.addClassName("papers-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Define columns
        grid.addColumn(Paper::getTitle).setHeader("Title").setAutoWidth(true).setFlexGrow(2);
        
        grid.addColumn(paper -> {
            String[] authors = paper.getAuthors();
            if (authors != null && authors.length > 0) {
                return String.join(", ", authors);
            }
            return "";
        }).setHeader("Authors").setAutoWidth(true).setFlexGrow(1);
        
        grid.addColumn(Paper::getJournal).setHeader("Journal").setAutoWidth(true);
        grid.addColumn(Paper::getYear).setHeader("Year").setAutoWidth(true);
        
        // Status column with colored badge
        grid.addColumn(new ComponentRenderer<>(paper -> {
            String status = paper.getStatus();
            Span badge = new Span(status);
            badge.getElement().getThemeList().add("badge " + status.toLowerCase());
            return badge;
        })).setHeader("Status").setAutoWidth(true);
        
        // Created date column with formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        grid.addColumn(paper -> paper.getCreatedAt() != null ? 
                formatter.format(paper.getCreatedAt()) : "")
                .setHeader("Uploaded").setAutoWidth(true);
        
        // Actions column
        grid.addColumn(actionsRenderer)
                .setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        // Add selection listener for double-click
        grid.addItemClickListener(event -> {
            if (event.getClickCount() == 2 && itemClickHandler != null) {
                itemClickHandler.accept(event.getItem());
            }
        });
    }

    /**
     * Updates the grid with papers based on search and filter criteria
     */
    public static Page<Paper> fetchPapersList(User currentUser, String searchTerm, String statusValue, 
                                             int page, int pageSize, PaperService paperService) {
        // Fetch papers based on filters
        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        if (!searchTerm.isEmpty() && !"All".equals(statusValue)) {
            // Search with status filter
            Page<Paper> searchResults = paperService.searchPapersByUser(currentUser, searchTerm, pageRequest);
            // Since we can't filter a Page directly, we'll load all matching papers and filter
            List<Paper> filteredPapers = searchResults.getContent().stream()
                    .filter(paper -> paper.getStatus().equals(statusValue))
                    .toList();
            
            // Apply pagination manually (this is simplified and not ideal for large datasets)
            return new org.springframework.data.domain.PageImpl<>(
                filteredPapers, 
                pageRequest, 
                searchResults.getTotalElements()
            );
        } else if (!searchTerm.isEmpty()) {
            // Search without status filter
            return paperService.searchPapersByUser(currentUser, searchTerm, pageRequest);
        } else if (!"All".equals(statusValue)) {
            // Status filter without search
            return paperService.getPapersByUserAndStatus(currentUser, statusValue, pageRequest);
        } else {
            // No filters
            return paperService.getPapersByUser(currentUser, pageRequest);
        }
    }

    /**
     * Update pagination information
     */
    public static int updatePagination(int currentPage, int totalPages, long totalItems, Span pageInfo,
                                    Button prevButton, Button nextButton) {
        pageInfo.setText((currentPage + 1) + " of " + totalPages + " (" + totalItems + " items)");
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < totalPages);
        
        // If current page is beyond total pages, adjust and return new page
        if (totalPages > 0 && currentPage >= totalPages) {
            return totalPages - 1;
        }
        
        return currentPage;
    }

    /**
     * Validate upload form fields
     */
    public static void validateUploadForm(TextField titleField, TextField authorsField, boolean fileUploaded, Button submitButton) {
        boolean titleValid = !titleField.isEmpty();
        boolean authorsValid = !authorsField.isEmpty();
        
        submitButton.setEnabled(titleValid && authorsValid && fileUploaded);
    }

    /**
     * Create a MultipartFile from a MemoryBuffer
     */
    public static MultipartFile createMultipartFileFromBuffer(MemoryBuffer buffer) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }
            
            @Override
            public String getOriginalFilename() {
                return buffer.getFileName();
            }
            
            @Override
            public String getContentType() {
                // Determine content type from file name
                String fileName = buffer.getFileName();
                if (fileName.endsWith(".pdf")) {
                    return "application/pdf";
                } else if (fileName.endsWith(".doc")) {
                    return "application/msword";
                } else if (fileName.endsWith(".docx")) {
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                } else {
                    return "application/octet-stream";
                }
            }
            
            @Override
            public boolean isEmpty() {
                return buffer.getInputStream() == null;
            }
            
            @Override
            public long getSize() {
                try {
                    return buffer.getInputStream().available();
                } catch (Exception e) {
                    return 0;
                }
            }
            
            @Override
            public byte[] getBytes() throws IOException {
                return buffer.getInputStream().readAllBytes();
            }
            
            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return buffer.getInputStream();
            }
            
            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    out.write(getBytes());
                }
            }
        };
    }

    /**
     * Download a paper by creating a stream resource and triggering download
     */
    public static void downloadPaper(Paper paper, UI ui, HasComponents viewComponent) {
        try {
            String filePath = paper.getFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                if (file.exists()) {
                    // Create a stream resource for the file
                    String fileName = file.getName();
                    StreamResource resource = new StreamResource(fileName, () -> {
                        try {
                            return new ByteArrayInputStream(Files.readAllBytes(Paths.get(filePath)));
                        } catch (Exception e) {
                            Notification.show("Download failed: " + e.getMessage(), 
                                3000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            return new ByteArrayInputStream(new byte[0]);
                        }
                    });
                    
                    // Create an anchor component for download and trigger it
                    Anchor downloadLink = new Anchor(resource, "");
                    downloadLink.setTarget("_blank");
                    downloadLink.getElement().setAttribute("download", true);
                    
                    // Add to UI temporarily and click it
                    if (ui != null) {
                        ui.add(downloadLink);
                        ui.getPage().executeJs("$0.click()", downloadLink.getElement());
                        // Clean up after click handled
                        ui.access(() -> ui.remove(downloadLink));
                    }
                } else {
                    Notification.show("File not found", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } else {
                Notification.show("No file associated with this paper", 
                    3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        } catch (Exception e) {
            Notification.show("Error: " + e.getMessage(), 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
