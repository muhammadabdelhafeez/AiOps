package org.kfh.aiops.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mockito;

class SettingsControllerTest {

    @Test
    void shouldDelegateGetToServiceForActiveCountryContext() {
        var service = Mockito.mock(SettingsService.class);
        var controller = new SettingsController(service);
        var kwContext = ctx("KW");
        var kwResponse = Map.<String, Object>of(
                "azureOpenAI", Map.of("integrations", List.of(
                        Map.of("id", "aoai-kw", "name", "KW GPT", "countryCodes", List.of("KW", "BH")),
                        Map.of("id", "aoai-all", "name", "Global GPT", "countryCodes", List.of("ALL")))));
        when(service.get(kwContext)).thenReturn(kwResponse);

        var response = controller.get(kwContext);

        assertThat(response).isEqualTo(kwResponse);
        assertThat(response.toString()).contains("KW GPT", "Global GPT");
        verify(service).get(kwContext);
    }

    @Test
    void shouldReturnCountryFilteredSettingsWhenCountryContextChanges() {
        var service = Mockito.mock(SettingsService.class);
        var controller = new SettingsController(service);
        var kwContext = ctx("KW");
        var egContext = ctx("EG");
        when(service.get(kwContext)).thenReturn(Map.<String, Object>of(
                "azureOpenAI", Map.of("integrations", List.of(
                        Map.of("id", "aoai-kw-bh", "name", "KW BH GPT", "countryCodes", List.of("KW", "BH")),
                        Map.of("id", "aoai-all", "name", "Global GPT", "countryCodes", List.of("ALL"))))));
        when(service.get(egContext)).thenReturn(Map.<String, Object>of(
                "azureOpenAI", Map.of("integrations", List.of(
                        Map.of("id", "aoai-all", "name", "Global GPT", "countryCodes", List.of("ALL"))))));

        var kwResponse = controller.get(kwContext);
        var egResponse = controller.get(egContext);

        assertThat(kwResponse.toString()).contains("KW BH GPT", "Global GPT");
        assertThat(egResponse.toString()).contains("Global GPT").doesNotContain("KW BH GPT");
        verify(service).get(kwContext);
        verify(service).get(egContext);
    }

    @Test
    void shouldDelegateUpdateToServiceWithTenantScopedContext() {
        var service = Mockito.mock(SettingsService.class);
        var controller = new SettingsController(service);
        var context = ctx("BH");
        var request = Map.<String, Object>of("azureOpenAI", Map.of("integrations", List.of(
                Map.of("id", "aoai-bh", "name", "BH GPT", "countryCodes", List.of("BH")))));
        when(service.update(context, request)).thenReturn(request);

        var response = controller.update(context, request);

        assertThat(response).isEqualTo(request);
        verify(service).update(context, request);
    }

    private static TenantContext ctx(String countryCode) {
        return new TenantContext(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"),
                countryCode,
                "PROD",
                "settings-controller-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE"));
    }
}
