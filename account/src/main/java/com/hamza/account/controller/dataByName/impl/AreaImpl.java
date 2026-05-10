package com.hamza.account.controller.dataByName.impl;

import com.hamza.account.controller.dataByName.AreaInterface;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Area;
import com.hamza.account.service.AreaService;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;
import java.util.function.ToIntFunction;

public class AreaImpl implements AreaInterface<Area> {

    private final DataPublisher dataPublisher;
    private final AreaService areaService = ServiceRegistry.get(AreaService.class);

    public AreaImpl(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }
    @Override
    public Class<Area> classData() {
        return Area.class;
    }

    @Override
    public List<? extends Area> listData() throws Exception {
        return areaService.fetchAllAreas();
    }

    @Override
    public Area object(int id, String name) {
        Area area = new Area();
        area.setId(id);
        area.setArea_name(name);
        return area;
    }

    @Override
    public int getId(Area area) {
        return area.getId();
    }

    @Override
    public String getName(Area area) {
        return area.getArea_name();
    }

    @Override
    public void setName(Area area, String name) {

    }

    @Override
    public int insert(Area area) throws Exception {
        return areaService.insertArea(area);
    }

    @Override
    public int update(Area area) throws Exception {
        return areaService.updateArea(area);
    }

    @Override
    public Area getDataById(int code) throws Exception {
        return null;
    }

    @Override
    public int deleteData(int code) throws Exception {
        return areaService.deleteArea(code);
    }

    @Override
    public ToIntFunction<Area> getIdFunction() {
        return Area::getId;
    }

    @Override
    public Publisher<String> publisherTable() {
        return dataPublisher.getPublisherAddStock();
    }
}
