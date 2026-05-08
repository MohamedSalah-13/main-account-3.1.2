package com.hamza.account.controller.search;

import java.util.List;

public interface SearchInterface<T> {

    Class<? super T> getSearchClass();

    List<T> searchItems() throws Exception;

    String getName(T t);

    default boolean selectMultiple() {
        return false;
    }

    List<T> getFilterItems(String filter) throws Exception;

}
