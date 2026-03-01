package com.hamza.controlsfx.table;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Column<T> {
    private Class<T> type;
    private String fieldName;
    private String title;

}