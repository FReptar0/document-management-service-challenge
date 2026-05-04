package com.clara.ops.challenge.dms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectKeyStrategyTest {

  private static final UUID DOC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void forUpload_uses_user_slash_id_double_underscore_name_with_pdf_suffix() {
    String key = ObjectKeyStrategy.forUpload("alice", DOC_ID, "Quarterly Report.pdf");
    assertThat(key).isEqualTo("alice/11111111-1111-1111-1111-111111111111__quarterly-report.pdf");
  }

  @Test
  void sanitize_lowercases_and_replaces_whitespace_with_dash() {
    assertThat(ObjectKeyStrategy.sanitize("  Hello World  ")).isEqualTo("hello-world.pdf");
  }

  @Test
  void sanitize_strips_unsafe_characters() {
    assertThat(ObjectKeyStrategy.sanitize("name?with*specials!.pdf"))
        .isEqualTo("namewithspecials.pdf");
  }

  @Test
  void sanitize_caps_extremely_long_names() {
    String long500 = "x".repeat(500);
    String result = ObjectKeyStrategy.sanitize(long500);
    // 200 chars sanitized + ".pdf" = 204 chars total
    assertThat(result).hasSize(204).endsWith(".pdf");
  }

  @Test
  void sanitize_uses_fallback_when_input_has_no_safe_chars() {
    assertThat(ObjectKeyStrategy.sanitize("???")).isEqualTo("document.pdf");
  }

  @Test
  void sanitize_leaves_already_pdf_suffixed_name_alone() {
    assertThat(ObjectKeyStrategy.sanitize("report.pdf")).isEqualTo("report.pdf");
  }

  @Test
  void sanitize_appends_pdf_when_missing() {
    assertThat(ObjectKeyStrategy.sanitize("report")).isEqualTo("report.pdf");
  }
}
