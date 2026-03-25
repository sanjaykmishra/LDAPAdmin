package com.ldapadmin.service;

import com.ldapadmin.service.hr.BambooHrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BambooHrClientTest {

    private BambooHrClient client;

    @BeforeEach
    void setUp() {
        client = new BambooHrClient();
    }

    @Test
    void parseEmployeeDirectory_withEmployeesArray_returnsAllEmployees() {
        String json = """
                {
                  "employees": [
                    {"id": "1", "firstName": "John", "lastName": "Doe", "workEmail": "john@example.com", "status": "Active"},
                    {"id": "2", "firstName": "Jane", "lastName": "Smith", "workEmail": "jane@example.com", "status": "Active"}
                  ]
                }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("firstName")).isEqualTo("John");
        assertThat(result.get(0).get("workEmail")).isEqualTo("john@example.com");
        assertThat(result.get(1).get("lastName")).isEqualTo("Smith");
    }

    @Test
    void parseEmployeeDirectory_withTerminatedEmployee_parsesStatusField() {
        String json = """
                {
                  "employees": [
                    {"id": "1", "firstName": "Alex", "lastName": "Gone", "status": "Terminated", "terminationDate": "2024-01-15"}
                  ]
                }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("status")).isEqualTo("Terminated");
        assertThat(result.get(0).get("terminationDate")).isEqualTo("2024-01-15");
    }

    @Test
    void parseEmployeeDirectory_emptyArray_returnsEmptyList() {
        String json = """
                { "employees": [] }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).isEmpty();
    }

    @Test
    void parseEmployeeDirectory_noEmployeesKey_returnsEmptyList() {
        String json = """
                { "status": "ok" }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).isEmpty();
    }

    @Test
    void parseEmployeeDirectory_malformedJson_throws() {
        assertThatThrownBy(() -> client.parseEmployeeDirectory("not json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void parseEmployeeDirectory_withAllFields_mapsAllFields() {
        String json = """
                {
                  "employees": [
                    {
                      "id": "42",
                      "firstName": "Bob",
                      "lastName": "Builder",
                      "displayName": "Bob Builder",
                      "workEmail": "bob@example.com",
                      "department": "Engineering",
                      "jobTitle": "Developer",
                      "status": "Active",
                      "hireDate": "2020-03-15",
                      "supervisorId": "10",
                      "supervisorEmail": "mgr@example.com"
                    }
                  ]
                }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).hasSize(1);
        Map<String, String> emp = result.get(0);
        assertThat(emp.get("id")).isEqualTo("42");
        assertThat(emp.get("department")).isEqualTo("Engineering");
        assertThat(emp.get("jobTitle")).isEqualTo("Developer");
        assertThat(emp.get("hireDate")).isEqualTo("2020-03-15");
        assertThat(emp.get("supervisorId")).isEqualTo("10");
    }

    @Test
    void parseEmployeeDirectory_withNullFields_handlesGracefully() {
        String json = """
                {
                  "employees": [
                    {"id": "1", "firstName": null, "lastName": "Test", "status": "Active"}
                  ]
                }
                """;

        List<Map<String, String>> result = client.parseEmployeeDirectory(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("firstName")).isNull();
        assertThat(result.get(0).get("lastName")).isEqualTo("Test");
    }
}
