package org.kfh.aiops.platform.country;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CountryRegistry {

    private final Set<String> enabledCountries;

    @Autowired
    public CountryRegistry(@Value("${kfh.countries.enabled:KW,BH,EG}") String countries) {
        this(parseCountries(countries));
    }

    public CountryRegistry(Set<String> enabledCountries) {
        this.enabledCountries = enabledCountries.stream()
                .map(CountryRegistry::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEnabled(String countryCode) {
        return enabledCountries.contains(normalize(countryCode));
    }

    public Set<String> enabledCountries() {
        return enabledCountries;
    }

    private static Set<String> parseCountries(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(country -> !country.isBlank())
                .collect(Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

