/*
 * Copyright (c) 2010-2021 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.rt.dataobject.migration;

import static org.eclipse.scout.rt.testing.platform.util.ScoutAssert.assertEqualsWithComparisonFailure;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.scout.rt.dataobject.DataObjectHelper;
import org.eclipse.scout.rt.dataobject.DoEntityBuilder;
import org.eclipse.scout.rt.dataobject.DoEntityHolder;
import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.dataobject.migration.DoStructureMigrator.DoStructureMigratorResult;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseFixtureDo;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseFixtureDoStructureMigrationHandler_3;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseFixtureDoValueMigrationHandler_1;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseTypeFixtureDoValueMigrationHandler_2;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseTypeFixtureStringId;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.HouseTypesFixture;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.PetFixtureAlwaysAcceptDoValueMigrationHandler_3;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.PetFixtureDo;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.PetFixtureDoValueMigrationHandler_3;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomFixtureDo;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomSizeFixtureDoValueMigrationHandler_2;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomTypeFixtureDoValueMigrationHandler_2;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomTypeFixtureStringId;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomTypesCollectionFixtureDo;
import org.eclipse.scout.rt.dataobject.migration.fixture.house.RoomTypesFixture;
import org.eclipse.scout.rt.dataobject.migration.fixture.version.CharlieFixtureTypeVersions.CharlieFixture_2;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.BeanMetaData;
import org.eclipse.scout.rt.platform.IBean;
import org.eclipse.scout.rt.platform.util.CollectionUtility;
import org.eclipse.scout.rt.platform.util.ImmutablePair;
import org.eclipse.scout.rt.testing.platform.BeanTestingHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link DoStructureMigrator}, with focus on data object value migrations ({@link IDoValueMigrationHandler}).
 */
public class DataObjectMigratorValueMigrationTest {

  protected static final RoomTypeFixtureStringId ROOM_TYPE_STANDARD_ROOM = RoomTypeFixtureStringId.of("standard-room"); // 'standard-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2
  protected static final RoomTypeFixtureStringId ROOM_TYPE_SMALL_ROOM = RoomTypeFixtureStringId.of("small-room"); // 'small-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2

  private static final List<IBean<?>> TEST_BEANS = new ArrayList<>();

  private static DoStructureMigrationContext s_migrationContext;
  private static DoStructureMigrator s_migrator;

  @BeforeClass
  public static void beforeClass() {
    DoStructureMigrationTestHelper testHelper = BEANS.get(DoStructureMigrationTestHelper.class);
    TestDoStructureMigrationInventory inventory = new TestDoStructureMigrationInventory(
        testHelper.getFixtureNamespaces(),
        testHelper.getFixtureTypeVersions(),
        Collections.emptyList(),
        Arrays.asList(new HouseFixtureDoStructureMigrationHandler_3()),
        Arrays.asList(
            new PetFixtureDoValueMigrationHandler_3(),
            new PetFixtureAlwaysAcceptDoValueMigrationHandler_3(),
            new RoomSizeFixtureDoValueMigrationHandler_2(),
            new RoomTypeFixtureDoValueMigrationHandler_2(),
            new HouseTypeFixtureDoValueMigrationHandler_2(),
            new HouseFixtureDoValueMigrationHandler_1()));

    TEST_BEANS.add(BEANS.get(BeanTestingHelper.class).registerBean(new BeanMetaData(TestDoStructureMigrationInventory.class, inventory).withReplace(true)));

    s_migrationContext = BEANS.get(DoStructureMigrationContext.class)
        .putGlobal(BEANS.get(DoValueMigrationIdsContextData.class)
            // by default, all value migrations are executed, except RoomSizeFixtureDoValueMigrationHandler_2 (PetFixtureAlwaysAcceptDoValueMigrationHandler_3 always accepts)
            .withAppliedValueMigrationIds(CollectionUtility.hashSet(PetFixtureAlwaysAcceptDoValueMigrationHandler_3.ID, RoomSizeFixtureDoValueMigrationHandler_2.ID)));
    s_migrator = BEANS.get(DoStructureMigrator.class);
  }

  @AfterClass
  public static void afterClass() {
    BEANS.get(BeanTestingHelper.class).unregisterBeans(TEST_BEANS);
  }

  /**
   * Tests applied value migrations provided in context.
   */
  @Test
  public void testAppliedValueMigrationHandlers() {
    RoomFixtureDo original = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withAreaInSquareMeter(10);

    // no value migrations will match the provided data object
    // RoomSizeFixtureDoValueMigrationHandler_2 is ignored by default in s_migrationContext
    DoStructureMigratorResult<RoomFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertFalse(result.isChanged()); // no changes (structure nor values changed)

    RoomFixtureDo expected = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withAreaInSquareMeter(10);

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests non-idempotent value migration with {@link RoomSizeFixtureDoValueMigrationHandler_2}.<br>
   * Migration handler matches and replaces whole data object (RoomFixtureDo).
   */
  @Test
  public void testNonIdempotentValueMigration() {
    RoomFixtureDo original = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withAreaInSquareMeter(10);

    DoStructureMigrationContext ctx = BEANS.get(DoStructureMigrationContext.class)
        .putGlobal(BEANS.get(DoValueMigrationIdsContextData.class)
            .withAppliedValueMigrationIds(Collections.emptySet())); // run all value migrations

    // migration run 1
    DoStructureMigratorResult<RoomFixtureDo> result = s_migrator.applyValueMigration(ctx, original);

    assertTrue(result.isChanged());

    RoomFixtureDo expected = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withAreaInSquareMeter(20); // increased by 10 through RoomSizeFixtureDoValueMigrationHandler_2

    assertEqualsWithComparisonFailure(expected, result.getDataObject());

    // migration run 2: data object is changed again, due to non-idempotent value migration
    result = s_migrator.applyValueMigration(ctx, result.getDataObject());

    assertTrue(result.isChanged());

    expected = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withAreaInSquareMeter(30); // increased by 10 through RoomSizeFixtureDoValueMigrationHandler_2

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests StringId value rename with {@link HouseTypeFixtureDoValueMigrationHandler_2}.<br>
   * Migration handler matches specific ID type.
   */
  @Test
  public void testRenameStringIdValue() {
    HouseFixtureDo original = BEANS.get(HouseFixtureDo.class)
        .withName("example")
        .withHouseType(HouseTypeFixtureStringId.of("house")); // will be migrated by HouseTypeFixtureDoValueMigrationHandler_2

    // migration run 1
    DoStructureMigratorResult<HouseFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    HouseFixtureDo expected = BEANS.get(HouseFixtureDo.class)
        .withName("example")
        .withHouseType(HouseTypesFixture.DETACHED_HOUSE);

    assertEqualsWithComparisonFailure(expected, result.getDataObject());

    // migration run 2: re-apply the same (idempotent) value migration, no changes should occur
    result = s_migrator.applyValueMigration(s_migrationContext, result.getDataObject());

    assertFalse(result.isChanged());

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests recursive value migrations with {@link HouseFixtureDoValueMigrationHandler_1} and
   * {@link RoomTypeFixtureDoValueMigrationHandler_2}.<br>
   * Migration handler matches and replaces whole data object (RoomFixtureDo).
   */
  @Test
  public void testReplaceDataObject() {
    HouseFixtureDo original = BEANS.get(HouseFixtureDo.class)
        .withName("tiny house")
        .withRooms(BEANS.get(RoomFixtureDo.class)
            .withName("tiny room"));

    DoStructureMigratorResult<HouseFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    HouseFixtureDo expected = BEANS.get(HouseFixtureDo.class)
        .withName("tiny house")
        .withRooms(BEANS.get(RoomFixtureDo.class)
            .withName("migrated tiny room")
            .withRoomType(RoomTypesFixture.ROOM));

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Value migration handler for {@link PetFixtureDo} matches a generic DoValue<IDoEntity> node.
   */
  @Test
  public void testAssignableValueClass() {
    RoomFixtureDo original = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withCustomData(BEANS.get(PetFixtureDo.class)
            .withName("Name: Fluffy")); // Will be migrated by PetFixtureDoValueMigrationHandler_3

    DoStructureMigratorResult<RoomFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    RoomFixtureDo expected = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withCustomData(BEANS.get(PetFixtureDo.class)
            .withName("Fluffy")); // migrated from "Name: Fluffy" to "Fluffy" by PetFixtureDoValueMigrationHandler_3

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Value migration handler for {@link PetFixtureDo} that always accepts (thus ignoring that already applied).
   */
  @Test
  public void testAlwaysAcceptMigrationHandler() {
    RoomFixtureDo original = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withCustomData(BEANS.get(PetFixtureDo.class)
            .withName("Nickname: Fluffy")); // Will be migrated by PetFixtureAlwaysAcceptDoValueMigrationHandler_3

    // check that part of the set of applied value migration IDs
    assertTrue(s_migrationContext.getGlobal(DoValueMigrationIdsContextData.class).getAppliedValueMigrationIds().contains(PetFixtureAlwaysAcceptDoValueMigrationHandler_3.ID));
    DoStructureMigratorResult<RoomFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    // executed even if already applied
    assertTrue(result.isChanged());

    RoomFixtureDo expected = BEANS.get(RoomFixtureDo.class)
        .withName("example")
        .withCustomData(BEANS.get(PetFixtureDo.class)
            .withName("Fluffy")); // migrated from "Nickname: Fluffy" to "Fluffy" by PetFixtureAlwaysAcceptDoValueMigrationHandler_3

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests combined data object structure and value migration with {@link HouseFixtureDoStructureMigrationHandler_3} and
   * {@link RoomTypeFixtureDoValueMigrationHandler_2}.
   */
  @Test
  public void testStructureAndValueMigrations() {
    // raw DoEntity data object
    IDoEntity original = BEANS.get(DoEntityBuilder.class)
        .put("_type", "charlieFixture.HouseFixture")
        .put("_typeVersion", CharlieFixture_2.VERSION.unwrap()) // will be updated by HouseFixtureDoStructureMigrationHandler_3
        .build();

    DoStructureMigratorResult<IDoEntity> result = s_migrator.migrateDataObject(s_migrationContext, original, IDoEntity.class);

    assertTrue(result.isChanged());

    RoomFixtureDo room1 = BEANS.get(RoomFixtureDo.class)
        .withName("example room 1")
        .withRoomType(RoomTypesFixture.ROOM);
    RoomFixtureDo room2 = BEANS.get(RoomFixtureDo.class)
        .withName("example room 2")
        .withRoomType(RoomTypesFixture.ROOM); // changed by RoomTypeFixtureDoValueMigrationHandler_2
    HouseFixtureDo expected = BEANS.get(HouseFixtureDo.class)
        .withRooms(room1, room2);

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Similar as {@link #testStructureAndValueMigrations()} but including an intermediate migrations.
   */
  @Test
  public void testStructureAndValueMigrationsWithIntermediateMigrations() {
    // raw DoEntity data object
    IDoEntity original = BEANS.get(DoEntityBuilder.class)
        .put("_type", "charlieFixture.HouseFixture")
        .put("_typeVersion", CharlieFixture_2.VERSION.unwrap()) // will be updated by HouseFixtureDoStructureMigrationHandler_3
        .build();

    DoStructureMigrationContext ctx = s_migrationContext.copy();

    // For all intermediate migrations, store typed data object for equality check afterwards and modify a copy

    DoEntityHolder<IDoEntity> intermediate1ResultHolder = new DoEntityHolder<>();
    ctx.getIntermediateMigrations().add((innerCtx, typedDataObject) -> {
      intermediate1ResultHolder.setValue((IDoEntity) typedDataObject);
      IDoEntity clonedDoEntity = BEANS.get(DataObjectHelper.class).clone((IDoEntity) typedDataObject);
      clonedDoEntity.put("intermediate", "ctx-1");
      return DoStructureMigratorResult.of(clonedDoEntity, true);
    });

    DoEntityHolder<IDoEntity> intermediate2ResultHolder = new DoEntityHolder<>();
    ctx.getIntermediateMigrations().add((innerCtx, typedDataObject) -> {
      intermediate2ResultHolder.setValue((IDoEntity) typedDataObject);
      IDoEntity clonedDoEntity = BEANS.get(DataObjectHelper.class).clone((IDoEntity) typedDataObject);
      clonedDoEntity.put("intermediate", "ctx-2");
      return DoStructureMigratorResult.of(clonedDoEntity, true);
    });

    DoEntityHolder<IDoEntity> localIntermediate1ResultHolder = new DoEntityHolder<>();
    IDataObjectIntermediateMigration<IDoEntity> localIntermediateMigration1 = (innerCtx, typedDataObject) -> {
      localIntermediate1ResultHolder.setValue(typedDataObject);
      IDoEntity clonedDoEntity = BEANS.get(DataObjectHelper.class).clone(typedDataObject);
      clonedDoEntity.put("intermediate", "local-1");
      return DoStructureMigratorResult.of(clonedDoEntity, true);
    };

    DoEntityHolder<IDoEntity> localIntermediate2ResultHolder = new DoEntityHolder<>();
    IDataObjectIntermediateMigration<IDoEntity> localIntermediateMigration2 = (innerCtx, typedDataObject) -> {
      localIntermediate2ResultHolder.setValue(typedDataObject);
      IDoEntity clonedDoEntity = BEANS.get(DataObjectHelper.class).clone(typedDataObject);
      clonedDoEntity.put("intermediate", "local-2");
      return DoStructureMigratorResult.of(clonedDoEntity, true);
    };

    DoStructureMigratorResult<IDoEntity> result = s_migrator.migrateDataObject(ctx, original, IDoEntity.class, Collections.emptyList(), CollectionUtility.arrayList(localIntermediateMigration1, localIntermediateMigration2));
    assertTrue(result.isChanged());

    // same for all checks
    RoomFixtureDo expectedRoom1 = BEANS.get(RoomFixtureDo.class)
        .withName("example room 1")
        .withRoomType(RoomTypesFixture.ROOM);

    // Verify results of intermediate migration
    RoomFixtureDo room2 = BEANS.get(RoomFixtureDo.class)
        .withName("example room 2")
        .withRoomType(RoomTypeFixtureStringId.of("standard-room")); // not changed yet by RoomTypeFixtureDoValueMigrationHandler_2 (only structure migration handlers were applied)
    HouseFixtureDo expected = BEANS.get(HouseFixtureDo.class)
        .withRooms(expectedRoom1, room2);

    assertEqualsWithComparisonFailure(expected, intermediate1ResultHolder.getValue());
    expected.put("intermediate", "ctx-1");
    assertEqualsWithComparisonFailure(expected, intermediate2ResultHolder.getValue());
    expected.put("intermediate", "ctx-2");
    assertEqualsWithComparisonFailure(expected, localIntermediate1ResultHolder.getValue());
    expected.put("intermediate", "local-1");
    assertEqualsWithComparisonFailure(expected, localIntermediate2ResultHolder.getValue());

    // Verify final result
    RoomFixtureDo expectedRoom2 = BEANS.get(RoomFixtureDo.class)
        .withName("example room 2")
        .withRoomType(RoomTypesFixture.ROOM); // changed by RoomTypeFixtureDoValueMigrationHandler_2
    expected = BEANS.get(HouseFixtureDo.class)
        .withRooms(expectedRoom1, expectedRoom2);

    expected.put("intermediate", "local-2");

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests the changed flag behavior of intermediate migrations.
   */
  @Test
  public void testIntermediateMigrationChangedFlag() {
    HouseFixtureDo original = BEANS.get(HouseFixtureDo.class);

    IDataObjectIntermediateMigration<IDoEntity> localIntermediateMigrationNoChange = (innerCtx, typedDataObject) -> {
      typedDataObject.put("normalized", true); // a non-relevant change
      return DoStructureMigratorResult.of(typedDataObject, false);
    };
    IDataObjectIntermediateMigration<IDoEntity> localIntermediateMigrationChange = (innerCtx, typedDataObject) -> {
      typedDataObject.put("intermediate", "changed");
      return DoStructureMigratorResult.of(typedDataObject, true);
    };

    // no change
    DoStructureMigratorResult<IDoEntity> result = s_migrator.migrateDataObject(s_migrationContext, original, IDoEntity.class, Collections.emptyList(), Collections.singletonList(localIntermediateMigrationNoChange));
    assertFalse(result.isChanged());
    HouseFixtureDo expected = BEANS.get(HouseFixtureDo.class);
    expected.put("normalized", true);
    assertEqualsWithComparisonFailure(expected, result.getDataObject());

    // change
    result = s_migrator.migrateDataObject(s_migrationContext, original, IDoEntity.class, Collections.emptyList(), Collections.singletonList(localIntermediateMigrationChange));
    assertTrue(result.isChanged());
    expected = BEANS.get(HouseFixtureDo.class);
    expected.put("intermediate", "changed");
    assertEqualsWithComparisonFailure(expected, result.getDataObject());

    // no change and change
    result = s_migrator.migrateDataObject(s_migrationContext, original, IDoEntity.class, Collections.emptyList(), CollectionUtility.arrayList(localIntermediateMigrationNoChange, localIntermediateMigrationChange));
    assertTrue(result.isChanged());
    expected = BEANS.get(HouseFixtureDo.class);
    expected.put("normalized", true);
    expected.put("intermediate", "changed");
    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests value migration for a DoList with duplicate values.
   */
  @Test
  public void testListValueMigration() {
    RoomTypesCollectionFixtureDo original = BEANS.get(RoomTypesCollectionFixtureDo.class)
        .withRoomTypesList(
            RoomTypesFixture.ROOM,
            RoomTypesFixture.LIVING_ROOM,
            ROOM_TYPE_STANDARD_ROOM, // 'standard-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2
            ROOM_TYPE_SMALL_ROOM); // 'small-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2

    DoStructureMigratorResult<RoomTypesCollectionFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    RoomTypesCollectionFixtureDo expected = BEANS.get(RoomTypesCollectionFixtureDo.class)
        .withRoomTypesList(
            RoomTypesFixture.ROOM,
            RoomTypesFixture.LIVING_ROOM,
            // Duplicate values in list are expected. Might be an inconsistent state depending on business logic, though.
            RoomTypesFixture.ROOM,
            RoomTypesFixture.ROOM);

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests value migration for a DoSet with duplicate values.
   */
  @Test
  public void testSetValueMigration() {
    RoomTypesCollectionFixtureDo original = BEANS.get(RoomTypesCollectionFixtureDo.class)
        .withRoomTypesSet(
            RoomTypesFixture.ROOM,
            RoomTypesFixture.LIVING_ROOM,
            ROOM_TYPE_STANDARD_ROOM, // 'standard-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2
            ROOM_TYPE_SMALL_ROOM); // 'small-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2

    DoStructureMigratorResult<RoomTypesCollectionFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    RoomTypesCollectionFixtureDo expected = BEANS.get(RoomTypesCollectionFixtureDo.class)
        .withRoomTypesSet(
            RoomTypesFixture.ROOM, // duplicate values in set have been removed
            RoomTypesFixture.LIVING_ROOM);

    assertEqualsWithComparisonFailure(expected, result.getDataObject());
  }

  /**
   * Tests value migration for a map with duplicate keys.
   */
  @Test
  public void testMapValueMigration() {
    RoomFixtureDo room = BEANS.get(RoomFixtureDo.class)
        .withName("room")
        .withRoomType(RoomTypesFixture.ROOM);
    RoomFixtureDo livingRoom = BEANS.get(RoomFixtureDo.class)
        .withName("living room")
        .withRoomType(RoomTypesFixture.LIVING_ROOM);
    RoomFixtureDo standardRoom = BEANS.get(RoomFixtureDo.class)
        .withName("standard room")
        .withRoomType(ROOM_TYPE_STANDARD_ROOM);
    RoomFixtureDo smallRoom = BEANS.get(RoomFixtureDo.class)
        .withName("small room")
        .withRoomType(ROOM_TYPE_SMALL_ROOM);

    RoomTypesCollectionFixtureDo original = BEANS.get(RoomTypesCollectionFixtureDo.class)
        .withRoomTypesMap(CollectionUtility.hashMap(
            ImmutablePair.of(RoomTypesFixture.ROOM, room),
            ImmutablePair.of(RoomTypesFixture.LIVING_ROOM, livingRoom),
            ImmutablePair.of(ROOM_TYPE_STANDARD_ROOM, standardRoom), // 'standard-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2
            ImmutablePair.of(ROOM_TYPE_SMALL_ROOM, smallRoom))); // 'small-room' will be migrated to 'room' by RoomTypeFixtureDoValueMigrationHandler_2

    DoStructureMigratorResult<RoomTypesCollectionFixtureDo> result = s_migrator.applyValueMigration(s_migrationContext, original);

    assertTrue(result.isChanged());

    Map<RoomTypeFixtureStringId, RoomFixtureDo> resultMap = result.getDataObject().getRoomTypesMap();
    assertEquals(2, resultMap.size());
    assertEquals("living room", resultMap.get(RoomTypesFixture.LIVING_ROOM).getName());

    // Merged keys result in a single, randomly selected value, depending on internal order of the original map and implementation details of AbstractReplacingDataObjectVisitor.
    String roomName = resultMap.get(RoomTypesFixture.ROOM).getName();
    assertTrue("room".equals(roomName) || "standard room".equals(roomName) || "small room".equals(roomName));
  }
}
