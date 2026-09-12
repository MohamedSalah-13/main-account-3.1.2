package com.hamza.account.features.employee;

/**
 * An employee named and numbered, and nothing else.
 * <p>
 * This is the projection {@code EmployeeService.getDelegateList} owed and never had. That
 * method handed back whole {@code Employees} rows to fill a dropdown - and the model carries
 * {@code salary}, so opening an invoice pulled every delegate's pay across the connection.
 * Nothing displayed it, which is exactly why it lasted: the leak was in what was fetched, not
 * in what was drawn. Its own javadoc recorded the debt and said the fix was "a projection
 * carrying an id and a name and nothing else".
 */
public record EmployeeRef(int id, String name) {
}
