package com.hamza.account.controller.search;

import java.util.List;

public interface SearchInterface<T> {

    Class<? super T> getSearchClass();

    List<T> searchItems() throws Exception;

    String getName(T t);

    default boolean selectMultiple() {
        return false;
    }

   default List<T> getFilterItems(String filter) throws Exception {
        //TODO 5/4/2026 10:58 AM Mohamed: must used in other implementations
        return List.of();
    }

}
