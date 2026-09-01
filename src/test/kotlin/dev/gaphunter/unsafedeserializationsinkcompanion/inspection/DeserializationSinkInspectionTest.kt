package dev.gaphunter.unsafedeserializationsinkcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DeserializationSinkInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(DeserializationSinkInspection::class.java)
    }

    fun `test direct chained readObject on a controller parameter is flagged`() {
        myFixture.configureByText(
            "UploadController.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;
            import org.springframework.web.bind.annotation.PostMapping;

            class UploadController {
                @PostMapping("/upload")
                Object handle(byte[] payload) throws Exception {
                    return new ObjectInputStream(new ByteArrayInputStream(payload)).readObject();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Deserialization of Untrusted Data") == true })
    }

    fun `test readObject called on a local variable is flagged`() {
        myFixture.configureByText(
            "UploadController2.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;
            import org.springframework.web.bind.annotation.PostMapping;

            class UploadController2 {
                @PostMapping("/upload")
                Object handle(byte[] payload) throws Exception {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload));
                    return ois.readObject();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Deserialization of Untrusted Data") == true })
    }

    fun `test a construction never actually read from is not flagged`() {
        myFixture.configureByText(
            "UploadController3.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;
            import org.springframework.web.bind.annotation.PostMapping;

            class UploadController3 {
                @PostMapping("/upload")
                void handle(byte[] payload) throws Exception {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(payload));
                    // never calls ois.readObject() -- dead construction, not a real sink
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Deserialization of Untrusted Data") == true })
    }

    fun `test a non-endpoint method is never flagged even with the same shape`() {
        myFixture.configureByText(
            "PlainHelper.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;

            class PlainHelper {
                Object handle(byte[] payload) throws Exception {
                    return new ObjectInputStream(new ByteArrayInputStream(payload)).readObject();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Deserialization of Untrusted Data") == true })
    }

    fun `test deserializing a value unrelated to any parameter is not flagged`() {
        myFixture.configureByText(
            "InternalController.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;
            import org.springframework.web.bind.annotation.GetMapping;

            class InternalController {
                private static final byte[] TRUSTED_CACHE = new byte[0];

                @GetMapping("/internal")
                Object handle(String unrelatedParam) throws Exception {
                    return new ObjectInputStream(new ByteArrayInputStream(TRUSTED_CACHE)).readObject();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Deserialization of Untrusted Data") == true })
    }

    fun `test JAX-RS POST annotation is also recognized`() {
        myFixture.configureByText(
            "JaxRsResource.java",
            """
            import java.io.ByteArrayInputStream;
            import java.io.ObjectInputStream;
            import javax.ws.rs.POST;

            class JaxRsResource {
                @POST
                Object handle(byte[] payload) throws Exception {
                    return new ObjectInputStream(new ByteArrayInputStream(payload)).readObject();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Deserialization of Untrusted Data") == true })
    }
}
