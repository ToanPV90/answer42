package com.samjdtechnologies.answer42.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.Project;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.model.enums.PipelineStatus;
import com.samjdtechnologies.answer42.repository.PaperRepository;

public class PaperServiceTest {

    @Mock
    private PaperRepository mockPaperRepository;
    
    @Mock
    private ObjectMapper mockObjectMapper;
    
    @Mock
    private CreditService mockCreditService;
    
    @Mock
    private FileTransferService mockFileTransferService;
    
    @Mock
    private PipelineJobLauncher mockPipelineJobLauncher;
    
    @Mock
    private MultipartFile mockFile;

    private PaperService paperService;
    private User testUser;
    private Paper testPaper;
    private UUID testPaperId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        paperService = new PaperService(
            mockPaperRepository, 
            mockObjectMapper, 
            mockCreditService, 
            mockFileTransferService,
            mockPipelineJobLauncher
        );
        
        testPaperId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        
        testPaper = new Paper();
        testPaper.setId(testPaperId);
        testPaper.setTitle("Test Paper");
        testPaper.setUser(testUser);
        testPaper.setStatus("UPLOADED");
        testPaper.setProcessingStatus(PipelineStatus.PENDING.getDisplayName());
        testPaper.setCreatedAt(ZonedDateTime.now());
    }

    @Test
    void testConstructor() {
        PaperService service = new PaperService(
            mockPaperRepository, 
            mockObjectMapper, 
            mockCreditService, 
            mockFileTransferService,
            mockPipelineJobLauncher
        );
        
        assertNotNull(service);
    }

    @Test
    void testSavePaper_NewPaper() {
        Paper newPaper = new Paper();
        newPaper.setTitle("New Paper");
        newPaper.setUser(testUser);
        
        Paper savedPaper = new Paper();
        savedPaper.setId(UUID.randomUUID());
        savedPaper.setTitle("New Paper");
        savedPaper.setUser(testUser);
        savedPaper.setCreatedAt(ZonedDateTime.now());
        savedPaper.setUpdatedAt(ZonedDateTime.now());
        
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(savedPaper);
        
        Paper result = paperService.savePaper(newPaper);
        
        assertNotNull(result);
        assertEquals("New Paper", result.getTitle());
        verify(mockPaperRepository).save(newPaper);
        assertNotNull(newPaper.getCreatedAt());
        assertNotNull(newPaper.getUpdatedAt());
    }

    @Test
    void testSavePaper_ExistingPaper() {
        testPaper.setCreatedAt(ZonedDateTime.now().minusDays(1));
        ZonedDateTime originalCreatedAt = testPaper.getCreatedAt();
        
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(testPaper);
        
        Paper result = paperService.savePaper(testPaper);
        
        assertNotNull(result);
        assertEquals(originalCreatedAt, result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(mockPaperRepository).save(testPaper);
    }

    @Test
    void testUploadPaper_Success_SmallFile() throws IOException {
        // Setup file mock
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getSize()).thenReturn(1024L); // Small file
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("test content".getBytes()));
        
        // Setup ObjectMapper mock
        ObjectNode mockMetadata = mock(ObjectNode.class);
        when(mockObjectMapper.createObjectNode()).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyString())).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyLong())).thenReturn(mockMetadata);
        
        // Setup credit service mock
        when(mockCreditService.hasEnoughCredits(any(UUID.class), anyInt())).thenReturn(true);
        
        // Setup pipeline job launcher mock
        when(mockPipelineJobLauncher.isPipelineAvailable()).thenReturn(true);
        when(mockPipelineJobLauncher.getRequiredCredits()).thenReturn(30);
        when(mockPipelineJobLauncher.launchPipelineProcessing(any(Paper.class), any(User.class))).thenReturn(true);
        
        // Setup repository mock
        when(mockPaperRepository.save(any(Paper.class))).thenAnswer(invocation -> {
            Paper paper = invocation.getArgument(0);
            paper.setId(testPaperId);
            return paper;
        });
        
        String[] authors = {"Author 1", "Author 2"};
        Paper result = paperService.uploadPaper(mockFile, "Test Title", authors, testUser);
        
        assertNotNull(result);
        assertEquals("Test Title", result.getTitle());
        assertEquals(Arrays.asList(authors), result.getAuthors());
        assertEquals(testUser, result.getUser());
        assertEquals("UPLOADED", result.getStatus());
        assertEquals(PipelineStatus.PENDING.getDisplayName(), result.getProcessingStatus());
        assertNotNull(result.getFilePath());
        assertEquals(1024L, result.getFileSize());
        assertEquals("application/pdf", result.getFileType());
        
        verify(mockCreditService).hasEnoughCredits(testUser.getId(), 30);
        verify(mockPipelineJobLauncher).launchPipelineProcessing(any(Paper.class), eq(testUser));
        verify(mockPaperRepository, times(2)).save(any(Paper.class)); // Once for initial save, once for status update
    }

    @Test
    void testUploadPaper_Success_LargeFile() throws IOException {
        // Setup file mock for large file
        when(mockFile.getOriginalFilename()).thenReturn("large_test.pdf");
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getSize()).thenReturn(50 * 1024 * 1024L); // 50MB - large file
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("large test content".getBytes()));
        
        // Setup FileTransferService mock
        CompletableFuture<Void> transferFuture = CompletableFuture.completedFuture(null);
        when(mockFileTransferService.transfer(eq(mockFile), any())).thenReturn(transferFuture);
        
        // Setup ObjectMapper mock
        ObjectNode mockMetadata = mock(ObjectNode.class);
        when(mockObjectMapper.createObjectNode()).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyString())).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyLong())).thenReturn(mockMetadata);
        
        // Setup credit service mock
        when(mockCreditService.hasEnoughCredits(any(UUID.class), anyInt())).thenReturn(true);
        
        // Setup pipeline job launcher mock
        when(mockPipelineJobLauncher.isPipelineAvailable()).thenReturn(true);
        when(mockPipelineJobLauncher.getRequiredCredits()).thenReturn(30);
        when(mockPipelineJobLauncher.launchPipelineProcessing(any(Paper.class), any(User.class))).thenReturn(true);
        
        // Setup repository mock
        when(mockPaperRepository.save(any(Paper.class))).thenAnswer(invocation -> {
            Paper paper = invocation.getArgument(0);
            paper.setId(testPaperId);
            return paper;
        });
        
        String[] authors = {"Author 1"};
        Paper result = paperService.uploadPaper(mockFile, "Large File Test", authors, testUser);
        
        assertNotNull(result);
        assertEquals("Large File Test", result.getTitle());
        assertEquals(50 * 1024 * 1024L, result.getFileSize());
        
        verify(mockFileTransferService).transfer(eq(mockFile), any());
        verify(mockPipelineJobLauncher).launchPipelineProcessing(any(Paper.class), eq(testUser));
    }

    @Test
    void testUploadPaper_InsufficientCredits() throws IOException {
        // Setup file mock
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("test content".getBytes()));
        
        // Setup ObjectMapper mock
        ObjectNode mockMetadata = mock(ObjectNode.class);
        when(mockObjectMapper.createObjectNode()).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyString())).thenReturn(mockMetadata);
        when(mockMetadata.put(anyString(), anyLong())).thenReturn(mockMetadata);
        
        // Setup credit service mock to return false
        when(mockCreditService.hasEnoughCredits(any(UUID.class), anyInt())).thenReturn(false);
        
        // Setup repository mock
        when(mockPaperRepository.save(any(Paper.class))).thenAnswer(invocation -> {
            Paper paper = invocation.getArgument(0);
            paper.setId(testPaperId);
            return paper;
        });
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        String[] authors = {"Author 1"};
        Paper result = paperService.uploadPaper(mockFile, "Test Title", authors, testUser);
        
        assertNotNull(result);
        verify(mockCreditService).hasEnoughCredits(testUser.getId(), 30);
        verify(mockPipelineJobLauncher, never()).launchPipelineProcessing(any(Paper.class), any(User.class));
    }

    @Test
    void testGetPaperById_Found() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        Optional<Paper> result = paperService.getPaperById(testPaperId);
        
        assertTrue(result.isPresent());
        assertEquals(testPaper, result.get());
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testGetPaperById_NotFound() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.empty());
        
        Optional<Paper> result = paperService.getPaperById(testPaperId);
        
        assertFalse(result.isPresent());
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testGetPapersByUser() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Paper> papers = Arrays.asList(testPaper);
        Page<Paper> paperPage = new PageImpl<>(papers, pageable, 1);
        
        when(mockPaperRepository.findByUser(testUser, pageable)).thenReturn(paperPage);
        
        Page<Paper> result = paperService.getPapersByUser(testUser, pageable);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testPaper, result.getContent().get(0));
        verify(mockPaperRepository).findByUser(testUser, pageable);
    }

    @Test
    void testDeletePaper_WithFile() {
        testPaper.setFilePath("/test/path/file.pdf");
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        paperService.deletePaper(testPaperId);
        
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository).deleteById(testPaperId);
    }

    @Test
    void testDeletePaper_NotFound() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.empty());
        
        paperService.deletePaper(testPaperId);
        
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository, never()).deleteById(any());
    }

    @Test
    void testCountPapersByUser() {
        when(mockPaperRepository.countByUser(testUser)).thenReturn(5L);
        
        long result = paperService.countPapersByUser(testUser);
        
        assertEquals(5L, result);
        verify(mockPaperRepository).countByUser(testUser);
    }

    @Test
    void testUpdatePaperMetadata_Success() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(testPaper);
        
        String[] newAuthors = {"New Author 1", "New Author 2"};
        Optional<Paper> result = paperService.updatePaperMetadata(
            testPaperId, "New Title", newAuthors, "New Abstract", "New Journal", 2024, "10.1000/test"
        );
        
        assertTrue(result.isPresent());
        Paper updatedPaper = result.get();
        assertEquals("New Title", updatedPaper.getTitle());
        assertEquals(Arrays.asList(newAuthors), updatedPaper.getAuthors());
        assertEquals("New Abstract", updatedPaper.getPaperAbstract());
        assertEquals("New Journal", updatedPaper.getJournal());
        assertEquals(Integer.valueOf(2024), updatedPaper.getYear());
        assertEquals("10.1000/test", updatedPaper.getDoi());
        assertNotNull(updatedPaper.getUpdatedAt());
        
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository).save(testPaper);
    }

    @Test
    void testUpdatePaperMetadata_NotFound() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.empty());
        
        Optional<Paper> result = paperService.updatePaperMetadata(
            testPaperId, "New Title", null, null, null, null, null
        );
        
        assertFalse(result.isPresent());
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository, never()).save(any());
    }

    @Test
    void testUpdatePaperPipelineStatus_Success() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(testPaper);
        
        Optional<Paper> result = paperService.updatePaperPipelineStatus(testPaperId, PipelineStatus.PROCESSING);
        
        assertTrue(result.isPresent());
        Paper updatedPaper = result.get();
        assertEquals(PipelineStatus.PROCESSING.getPaperStatus(), updatedPaper.getStatus());
        assertEquals(PipelineStatus.PROCESSING.getDisplayName(), updatedPaper.getProcessingStatus());
        assertNotNull(updatedPaper.getUpdatedAt());
        
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository).save(testPaper);
    }

    @Test
    void testGetPipelineStatus_Found() {
        testPaper.setProcessingStatus(PipelineStatus.PROCESSING.getDisplayName());
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        PipelineStatus result = paperService.getPipelineStatus(testPaperId);
        
        assertEquals(PipelineStatus.PROCESSING, result);
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testGetPipelineStatus_NotFound() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.empty());
        
        PipelineStatus result = paperService.getPipelineStatus(testPaperId);
        
        assertEquals(PipelineStatus.PENDING, result);
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testGetProcessingProgress() {
        testPaper.setProcessingStatus(PipelineStatus.PROCESSING.getDisplayName());
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        int result = paperService.getProcessingProgress(testPaperId);
        
        assertEquals(PipelineStatus.PROCESSING.getProgressPercentage(), result);
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testIsProcessingComplete_Complete() {
        testPaper.setProcessingStatus(PipelineStatus.COMPLETED.getDisplayName());
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        boolean result = paperService.isProcessingComplete(testPaperId);
        
        assertTrue(result);
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testIsProcessingComplete_InProgress() {
        testPaper.setProcessingStatus(PipelineStatus.PROCESSING.getDisplayName());
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        
        boolean result = paperService.isProcessingComplete(testPaperId);
        
        assertFalse(result);
        verify(mockPaperRepository).findById(testPaperId);
    }

    @Test
    void testSearchPapers() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Paper> papers = Arrays.asList(testPaper);
        Page<Paper> paperPage = new PageImpl<>(papers, pageable, 1);
        
        when(mockPaperRepository.searchPapers("test search", pageable)).thenReturn(paperPage);
        
        Page<Paper> result = paperService.searchPapers("test search", pageable);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(mockPaperRepository).searchPapers("test search", pageable);
    }

    @Test
    void testGetPapersNotInProject() {
        Paper paper1 = new Paper();
        paper1.setId(UUID.randomUUID());
        Paper paper2 = new Paper();
        paper2.setId(UUID.randomUUID());
        Paper paper3 = new Paper();
        paper3.setId(UUID.randomUUID());
        
        List<Paper> allUserPapers = Arrays.asList(paper1, paper2, paper3);
        when(mockPaperRepository.findByUser(testUser)).thenReturn(allUserPapers);
        
        Project project = new Project();
        project.setPapers(new HashSet<>(Arrays.asList(paper2))); // paper2 is in project
        
        List<Paper> result = paperService.getPapersNotInProject(testUser, project);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(paper1));
        assertTrue(result.contains(paper3));
        assertFalse(result.contains(paper2));
        verify(mockPaperRepository).findByUser(testUser);
    }

    @Test
    void testUpdatePaperVisibility_Success() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(testPaper);
        
        Optional<Paper> result = paperService.updatePaperVisibility(testPaperId, true);
        
        assertTrue(result.isPresent());
        assertTrue(result.get().getIsPublic());
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository).save(testPaper);
    }

    @Test
    void testUpdateTextContent_Success() {
        when(mockPaperRepository.findById(testPaperId)).thenReturn(Optional.of(testPaper));
        when(mockPaperRepository.save(any(Paper.class))).thenReturn(testPaper);
        
        Optional<Paper> result = paperService.updateTextContent(testPaperId, "New text content");
        
        assertTrue(result.isPresent());
        assertEquals("New text content", result.get().getTextContent());
        verify(mockPaperRepository).findById(testPaperId);
        verify(mockPaperRepository).save(testPaper);
    }
}
