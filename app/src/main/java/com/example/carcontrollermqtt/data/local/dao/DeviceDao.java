package com.example.carcontrollermqtt.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.carcontrollermqtt.data.models.Device;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;

@Dao
public interface DeviceDao {
    @Query("SELECT * FROM devices")
    LiveData<List<Device>> observeDevices();

    @Query("SELECT * FROM devices WHERE active = 1")
    Single<Device> getSelectedDevice();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertDevice(Device device);

    @Update
    Completable updateDevice(Device device);

    @Update
    Completable updateDevices(Device[] devices);

    @Delete
    Completable deleteDevice(Device device);
}
