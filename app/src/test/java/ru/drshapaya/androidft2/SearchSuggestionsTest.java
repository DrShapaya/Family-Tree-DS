package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SearchSuggestionsTest {
    @Test
    public void personSuggestionRetainsIdAndRelationCommand() {
        Person person = new Person("anna");
        person.name = "Анна Иванова";

        List<SearchSuggestions.Suggestion> result = SearchSuggestions.build(
            "предки ан", 8, Collections.singletonList(person),
            Collections::emptyList, Collections::emptyList, "Без имени");

        assertEquals(1, result.size());
        assertEquals("Анна Иванова", result.get(0).label);
        assertEquals("предки Анна Иванова", result.get(0).applyText);
        assertEquals("anna", result.get(0).personId);
    }

    @Test
    public void directRelationSuggestionKeepsCommand() {
        Person anna = new Person("anna");
        anna.name = "Анна Иванова";

        List<SearchSuggestions.Suggestion> result = SearchSuggestions.build(
            "дети ан", 8, Collections.singletonList(anna),
            Collections::emptyList, Collections::emptyList, "Без имени");

        assertEquals(1, result.size());
        assertEquals("дети Анна Иванова", result.get(0).applyText);
    }

    @Test
    public void placesAreDeduplicatedAndExactTextIsNotSuggested() {
        Person first = new Person("first");
        first.place = "Москва, Томск";
        Person second = new Person("second");
        second.place = "МОСКВА; Омск";
        List<String> places = SearchSuggestions.treePlaces(Arrays.asList(first, second));
        assertEquals(Arrays.asList("Москва", "Томск", "Омск"), places);

        List<SearchSuggestions.Suggestion> prefix = SearchSuggestions.build(
            "моск", 8, Collections.emptyList(), () -> places,
            Collections::emptyList, "Без имени");
        assertEquals(1, prefix.size());
        assertEquals("Москва", prefix.get(0).applyText);

        List<SearchSuggestions.Suggestion> exact = SearchSuggestions.build(
            "Москва", 8, Collections.emptyList(), () -> places,
            Collections::emptyList, "Без имени");
        assertTrue(exact.isEmpty());
    }

    @Test
    public void yearsAreSortedAndLimitIsHonored() {
        Person first = new Person("first");
        first.bornYear = "1902";
        Person second = new Person("second");
        second.bornYear = "1901";
        List<String> years = SearchSuggestions.birthYears(Arrays.asList(first, second, first));
        assertEquals(Arrays.asList("1901", "1902"), years);

        List<SearchSuggestions.Suggestion> result = SearchSuggestions.build(
            "19", 1, Collections.emptyList(), Collections::emptyList,
            () -> years, "Без имени");
        assertEquals(1, result.size());
        assertEquals("1901", result.get(0).label);
    }
}
