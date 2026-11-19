package org.example.demo1;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BMIResultServiceTest {
    private static final String INSERT_RESULT =
            "INSERT INTO bmi_results (weight, height, bmi, language) VALUES (?, ?, ?, ?)";

    @Test
    void insertsTheResultAndClosesDatabaseResources() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(INSERT_RESULT)).thenReturn(statement);

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            BMIResultService.saveResult(72.5, 1.75, 23.673469, "vi");
        }

        InOrder order = inOrder(statement, connection);
        order.verify(connection).prepareStatement(INSERT_RESULT);
        order.verify(statement).setDouble(1, 72.5);
        order.verify(statement).setDouble(2, 1.75);
        order.verify(statement).setDouble(3, 23.673469);
        order.verify(statement).setString(4, "vi");
        order.verify(statement).executeUpdate();
        order.verify(statement).close();
        order.verify(connection).close();
    }

    @Test
    void handlesAnUnavailableDatabase() {
        SQLException unavailable = new SQLException("Connection unavailable");
        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(unavailable);

            assertDoesNotThrow(() -> BMIResultService.saveResult(80, 2, 20, "en"));
            database.verify(() -> DriverManager.getConnection(anyString(), anyString(), anyString()));
        }
    }

    @Test
    void closesTheConnectionWhenPreparingTheInsertFails() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(INSERT_RESULT)).thenThrow(new SQLException("Prepare failed"));

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            assertDoesNotThrow(() -> BMIResultService.saveResult(80, 2, 20, "en"));
        }

        verify(connection).close();
    }

    @Test
    void closesDatabaseResourcesWhenTheInsertFails() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(INSERT_RESULT)).thenReturn(statement);
        when(statement.executeUpdate()).thenThrow(new SQLException("Insert failed"));

        try (MockedStatic<DriverManager> database = mockStatic(DriverManager.class)) {
            database.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(connection);

            assertDoesNotThrow(() -> BMIResultService.saveResult(80, 2, 20, "en"));
        }

        verify(statement).close();
        verify(connection).close();
    }
}
