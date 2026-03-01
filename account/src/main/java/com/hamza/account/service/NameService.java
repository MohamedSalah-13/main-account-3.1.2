package com.hamza.account.service;

import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.base.BaseNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @param <T> for Names (Customers or Suppliers)
 */
public record NameService<T extends BaseNames>(NameData<T> nameData) {

    public double getCredit(List<T> list, int num_id) {
        Function<T, Double> getCreditLimit = nameData.getCreditLimit();
        Optional<Double> first = list.stream().filter(e -> e.getId() == num_id).map(getCreditLimit).findFirst();
        return first.orElse(0.0);
    }

    public List<String> getNames(List<T> listName) {
        return new ArrayList<>(listName)
                .stream()
                .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
                .map(BaseNames::getName).toList();
    }

    public int getCodeByName(List<T> list, String s) {
        Optional<Integer> integer = list.stream()
                .filter(model -> s.equals(model.getName()))
                .map(BaseNames::getId).findFirst();
        return integer.orElse(0);
    }

    public T getObject(List<T> list, String s) {
        Optional<T> integer = list.stream()
                .filter(model -> s.equals(model.getName()))
                .findFirst();
        return integer.orElse(null);
    }

}
