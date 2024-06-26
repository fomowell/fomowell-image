package org.example;

import com.fasterxml.jackson.databind.util.JSONPObject;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${bee.node.url}")
    private String BEE_URL;

    @Value("${bee.batch.id}")
    private String BEE_BATCH_ID;

    @Value("${bee.default.reference}")
    private String DEFAULT_REFERENCE;

    private String localPath;

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFileTemp(@RequestParam("file") MultipartFile file) throws IOException {
        String uuid = UUID.randomUUID().toString();
        File tempFile = new File(localPath+uuid);
        file.transferTo(tempFile);
        return ResponseEntity.ok()
                .header("Content-type", ContentType.APPLICATION_JSON.getMimeType())
                .body("");
    }


    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("tmp-", file.getOriginalFilename());
        file.transferTo(tempFile);
        HttpPost uploadFile = new HttpPost(BEE_URL);
        FileEntity fileEntity = new FileEntity(tempFile, ContentType.APPLICATION_OCTET_STREAM);
        uploadFile.setEntity(fileEntity);
        uploadFile.addHeader("Content-type", ContentType.APPLICATION_OCTET_STREAM.getMimeType());
        uploadFile.addHeader("Swarm-Postage-Batch-Id", BEE_BATCH_ID);
        try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
            HttpEntity entity = response.getEntity();
            if (response.getCode() == 200 || response.getCode() == 201) {
                String body = EntityUtils.toString(entity);
                return ResponseEntity.ok().header("Content-type", ContentType.APPLICATION_JSON.getMimeType()).body(body);
            } else {
                if (entity != null) {
                    logger.error("upload file failed.entity:{}", EntityUtils.toString(entity));
                } else {
                    logger.error("upload file failed.response:{}", response);
                }
                return ResponseEntity.status(response.getCode()).body("File upload failed");
            }
        } catch (Exception e) {
            logger.error("upload file failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/download/{reference}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String reference) {

        ResponseEntity<byte[]> response = getResponse(reference);
        if (response.getBody() == null) {
            response = getResponse(DEFAULT_REFERENCE);
        }
        return response;
    }

    private ResponseEntity<byte[]> getResponse(String reference) {
        HttpGet downloadFile = new HttpGet(BEE_URL + "/" + reference);
        try (CloseableHttpResponse response = httpClient.execute(downloadFile)) {
            HttpEntity entity = response.getEntity();
            if (response.getCode() == 200) {
                byte[] fileBytes = EntityUtils.toByteArray(entity);
                return ResponseEntity.ok().body(fileBytes);
            } else {
                if (entity != null) {
                    logger.error("download file failed.entity:{}", EntityUtils.toString(entity));
                } else {
                    logger.error("download file failed.response:{}", response);
                }
                return ResponseEntity.status(response.getCode()).body(null);
            }
        } catch (Exception e) {
            logger.error("download file error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
