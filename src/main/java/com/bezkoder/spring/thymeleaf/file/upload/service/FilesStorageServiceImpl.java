package com.bezkoder.spring.thymeleaf.file.upload.service;

import com.adobe.pdfservices.operation.ExecutionContext;
import com.adobe.pdfservices.operation.auth.Credentials;
import com.adobe.pdfservices.operation.exception.SdkException;
import com.adobe.pdfservices.operation.exception.ServiceApiException;
import com.adobe.pdfservices.operation.exception.ServiceUsageException;
import com.adobe.pdfservices.operation.io.FileRef;
import com.adobe.pdfservices.operation.pdfops.ExtractPDFOperation;
import com.adobe.pdfservices.operation.pdfops.OCROperation;
import com.adobe.pdfservices.operation.pdfops.options.extractpdf.ExtractElementType;
import com.adobe.pdfservices.operation.pdfops.options.extractpdf.ExtractPDFOptions;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Stream;

@Service
public class FilesStorageServiceImpl implements FilesStorageService {
    private final Path root = Paths.get("uploads");

    @Override
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }

    @Override
    public void save(MultipartFile file) {
        try {
            Files.copy(file.getInputStream(), this.root.resolve(file.getOriginalFilename()));
        } catch (Exception e) {
            if (e instanceof FileAlreadyExistsException) {
                throw new RuntimeException("A file of that name already exists.");
            }

            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public Resource load(String filename) {
        try {
            Path file = root.resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    @Override
    public Resource extractTextInfoFromPDF(String filename) {
        try {
            // Initial setup, create credentials instance.
            Credentials credentials = Credentials.serviceAccountCredentialsBuilder()
                    .fromFile("pdfservices-api-credentials.json")
                    .build();

            // Create an ExecutionContext using credentials.
            ExecutionContext executionContext = ExecutionContext.create(credentials);
            // -------------------- OCR operation -------------------
            OCROperation ocrOperation = OCROperation.createNew();
            // Provide an input FileRef for the operation
            FileRef source = FileRef.createFromLocalFile("uploads/" + filename);
            ocrOperation.setInput(source);
            // Execute the operation
            FileRef ocrConvertResult = ocrOperation.execute(executionContext);
            // Save the result at the specified location
            String ocrConvertFilePath = createOutputPDFFilePath();
            ocrConvertResult.saveAs(ocrConvertFilePath);
            // -------------------- OCR operation -------------------

            // -------------------- Extract JSON operation -------------------
            ExtractPDFOperation extractPDFOperation = ExtractPDFOperation.createNew();
            FileRef ocrConvertedSource = FileRef.createFromLocalFile(ocrConvertFilePath);
            extractPDFOperation.setInputFile(ocrConvertedSource);

            // Build ExtractPDF options and set them into the operation
            ExtractPDFOptions extractPDFOptions = ExtractPDFOptions.extractPdfOptionsBuilder()
                    .addElementsToExtract(Arrays.asList(ExtractElementType.TEXT))
                    .build();
            extractPDFOperation.setOptions(extractPDFOptions);

            // Execute the operation
            FileRef jsonExtractResult = extractPDFOperation.execute(executionContext);

            // Save the result at the specified location
            String jsonExtractFilePath = createOutputFilePath();
            jsonExtractResult.saveAs(jsonExtractFilePath);
            Path zipFilePath = Paths.get(jsonExtractFilePath);
            Resource extractedResource = new UrlResource(zipFilePath.toUri());
            // -------------------- Extract JSON operation -------------------
            if (extractedResource.exists() || extractedResource.isReadable()) {
                return extractedResource;
            } else {
                throw new RuntimeException("Could not read the file!");
            }

        } catch (ServiceApiException | IOException | SdkException | ServiceUsageException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean delete(String filename) {
        try {
            Path file = root.resolve(filename);
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(root.toFile());
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.root, 1).filter(path -> !path.equals(this.root)).map(this.root::relativize);
        } catch (IOException e) {
            throw new RuntimeException("Could not load the files!");
        }
    }

    public static String createOutputPDFFilePath() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
        LocalDateTime now = LocalDateTime.now();
        String timeStamp = dateTimeFormatter.format(now);
        return ("output/ExtractTextInfoFromPDF/extract" + timeStamp + ".pdf");
    }

    public static String createOutputFilePath() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
        LocalDateTime now = LocalDateTime.now();
        String timeStamp = dateTimeFormatter.format(now);
        return ("output/ExtractTextInfoFromPDF/extract" + timeStamp + ".zip");
    }
}
