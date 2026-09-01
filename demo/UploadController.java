import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class UploadController {

    // Flagged: payload (an untrusted controller parameter) is
    // deserialized directly.
    @PostMapping("/upload")
    Object handleUpload(byte[] payload) throws Exception {
        return new ObjectInputStream(new ByteArrayInputStream(payload)).readObject();
    }

    // Flagged: same sink reached via a local variable.
    @PostMapping("/upload2")
    Object handleUpload2(byte[] payload) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload));
        return ois.readObject();
    }

    // Not flagged: deserializes a fixed, trusted internal value, not
    // any controller parameter.
    private static final byte[] TRUSTED_CACHE = new byte[0];

    @GetMapping("/internal")
    Object handleInternal(String unrelatedParam) throws Exception {
        return new ObjectInputStream(new ByteArrayInputStream(TRUSTED_CACHE)).readObject();
    }
}
