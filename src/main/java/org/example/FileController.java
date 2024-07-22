package org.example;

import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${bee.node.url}")
    private String BEE_URL;

    @Value("${bee.batch.id}")
    private String BEE_BATCH_ID;

    @Value("${bee.default.reference}")
    private String DEFAULT_REFERENCE;

    @Value("${bee.local.path}")
    private String localPath;

    @Value("${bee.local.switch}")
    private boolean switchLocal;

    @Autowired
    private TemporaryStorage temporaryStorage;

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    @PostMapping("/replace")
    public ResponseEntity<String> replaceFile(@RequestParam("file") String file) throws IOException {
        final String finalFile = file.trim().replaceAll("\\n","");
        String body = temporaryStorage.getValue(finalFile, () -> uploadBeeFile(finalFile));
        System.out.printf("%s:::%s ", finalFile, body);
        return responseWrapper(body);
    }

    private ResponseEntity<String> responseWrapper(String body) {
        if (body != null) {
            return ResponseEntity.ok().header("Content-type", ContentType.APPLICATION_JSON.getMimeType()).body(body);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        return uploadLocalFile(file);
    }

    public ResponseEntity<String> uploadLocalFile(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String md5= DigestUtils.md5Hex(bytes);
        // 创建File对象
        File directory = new File(localPath);
        // 检查目录是否存在
        if (!directory.exists()) {
            // 目录不存在，创建目录
            directory.mkdirs();
        }
        Path path = Paths.get(localPath + md5);
        if(!path.toFile().exists()){
            Files.write(path,bytes);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("reference", md5);
        return ResponseEntity.ok().header("Content-type", ContentType.APPLICATION_JSON.getMimeType())
                .body(jsonObject.toJSONString());
    }


    private String uploadBeeFile(String file) {
        File tempFile = new File(localPath + file);
        if (!tempFile.exists()) {
            return null;
        }
        HttpPost uploadFile = new HttpPost(BEE_URL);
        FileEntity fileEntity = new FileEntity(tempFile, ContentType.APPLICATION_OCTET_STREAM);
        uploadFile.setEntity(fileEntity);
        uploadFile.addHeader("Content-type", ContentType.APPLICATION_OCTET_STREAM.getMimeType());
        uploadFile.addHeader("Swarm-Postage-Batch-Id", BEE_BATCH_ID);
        try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
            HttpEntity entity = response.getEntity();
            if (response.getCode() == 200 || response.getCode() == 201) {
                //成功时删除
                tempFile.delete();
                return EntityUtils.toString(entity);
            } else {
                if (entity != null) {
                    logger.error("upload file failed.entity:{}", EntityUtils.toString(entity));
                } else {
                    logger.error("upload file failed.response:{}", response);
                }
            }
        } catch (Exception e) {
            logger.error("upload file failed", e);
        }
        return null;
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
