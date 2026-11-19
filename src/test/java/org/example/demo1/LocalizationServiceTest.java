package org.example.demo1;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationServiceTest {
    private static final String SELECT_TRANSLATIONS =
            "SELECT `key`, value FROM localization_strings WHERE language = ?";

    @Test
    void loadsAllTranslationsUsingTheLanguageCode() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(SELECT_TRANSLATIONS)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("key")).thenReturn("weight", "height");
        when(rows.getString("value")).thenReturn("وزن", "قد");

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            assertEquals(Map.of("weight", "وزن", "height", "قد"),
                    LocalizationService.getLocalizedStrings(Locale.forLanguageTag("fa-IR")));
        }

        verify(statement).setString(1, "fa");
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void returnsAnEmptyMapWhenTheLanguageHasNoTranslations() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(SELECT_TRANSLATIONS)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            assertTrue(LocalizationService.getLocalizedStrings(Locale.JAPAN).isEmpty());
        }

        verify(statement).setString(1, "ja");
    }

    @Test
    void returnsAnEmptyMapWhenTheDatabaseIsUnavailable() {
        SQLException unavailable = new SQLException("Connection unavailable");
        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(unavailable);

            assertTrue(LocalizationService.getLocalizedStrings(Locale.US).isEmpty());
            database.verify(() -> DriverManager.getConnection(anyString(), anyString(), anyString()));
        }
    }

    @Test
    void closesDatabaseResourcesWhenTheQueryFails() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(SELECT_TRANSLATIONS)).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("Query failed"));

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            assertTrue(LocalizationService.getLocalizedStrings(Locale.US).isEmpty());
        }

        verify(statement).close();
        verify(connection).close();
    }
}
