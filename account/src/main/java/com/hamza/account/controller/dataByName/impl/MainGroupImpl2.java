package com.hamza.account.controller.dataByName.impl;

import com.hamza.account.controller.dataByName.AreaInterface;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.base.BaseGroups;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.service.MainGroupService;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;
import java.util.function.ToIntFunction;

public class MainGroupImpl2 implements AreaInterface<BaseGroups> {

    private final DataPublisher dataPublisher;
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);

    public MainGroupImpl2(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }

    @Override
    public Class<BaseGroups> classData() {
        return BaseGroups.class;
    }

    @Override
    public List<? extends BaseGroups> listData() throws Exception {
        return mainGroupService.getMainGroupList();
    }

    @Override
    public MainGroups object(int id, String name) {
        MainGroups mainGroups = new MainGroups();
        mainGroups.setId(id);
        mainGroups.setName(name);
        return mainGroups;
    }

    @Override
    public int getId(BaseGroups mainGroups) {
        return mainGroups.getId();
    }

    @Override
    public String getName(BaseGroups mainGroups) {
        return mainGroups.getName();
    }

    @Override
    public void setName(BaseGroups mainGroups, String name) {

    }

    @Override
    public int insert(BaseGroups mainGroups) throws Exception {
        return mainGroupService.insert((MainGroups) mainGroups);
    }

    @Override
    public int update(BaseGroups mainGroups) throws Exception {
        return mainGroupService.update((MainGroups) mainGroups);
    }

    @Override
    public MainGroups getDataById(int code) throws Exception {
        return null;
    }

    @Override
    public int deleteData(int code) throws Exception {
        return mainGroupService.deleteMainGroup(code);
    }

    @Override
    public ToIntFunction<BaseGroups> getIdFunction() {
        return BaseGroups::getId;
    }

    @Override
    public Publisher<String> publisherTable() {
        return dataPublisher.getPublisherAddMainGroup();
    }
}
