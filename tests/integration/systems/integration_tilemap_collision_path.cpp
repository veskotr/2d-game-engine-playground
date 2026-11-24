#include <sle/renderer/Camera2D.hpp>
#include <sle/engine/TileCollisionSystem.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/physics/PhysicsWorld.hpp>

#include <box2d/box2d.h>
#include <filesystem>
#include <iostream>
#include <stdexcept>

namespace {

void checkAuthoredSlope()
{
    const auto path = std::filesystem::absolute("tests/data/tilemap/Tiles.sleproject");
    const auto project = sle::ProjectRuntime::open(path);
    if (!project.ok()) throw std::runtime_error(project.error());
    auto document = project.value().sceneDocument(project.value().startupScene());
    for (auto& layer : document["tileLayers"])
        if (layer["name"] == "Backdrop") layer["pixelsPerUnit"] = 2;
    const auto resolve = [&](const std::string& ref) { return project.value().resolveAsset(ref); };
    sle::entity::Scene scene;
    const auto loaded = sle::SceneLoader::loadData(document, scene, path.string(), {}, resolve);
    if (!loaded.ok()) throw std::runtime_error(loaded.error());

    sle::physics::PhysicsWorld world;
    sle::TileCollisionSystem collision(world);
    sle::core::Camera2D camera(30, 30);
    camera.setPosition({18, 10});
    collision.update(scene.getTileLayers(), camera);
    if (world.getRawWorld()->GetBodyCount() != 2)
        throw std::runtime_error("Decorative tile produced a body or a nearby slope is missing");

    bool foundSlope = false;
    world.visitTerrainEdges([&](glm::vec2 a, glm::vec2 b) {
        const glm::vec2 top{16, 18};
        const glm::vec2 toe{20, 16};
        const auto near = [](glm::vec2 x, glm::vec2 y) { return glm::length(x - y) < 0.01f; };
        foundSlope |= (near(a, top) && near(b, toe)) || (near(b, top) && near(a, toe));
    });
    if (!foundSlope) throw std::runtime_error("Native debug edges missed the scaled slope");

    sle::components::RigidBodyComponent dynamic;
    const auto player = world.createBody({17, 23}, dynamic);
    sle::components::CircleColliderComponent ball;
    ball.radius = 0.75f;
    ball.friction = 0;
    world.createCircleFixture(player, ball);
    for (int step = 0; step < 9; ++step) world.step(1.0f / 60.0f);
    glm::vec2 position;
    float rotation;
    world.getBodyTransform(player, position, rotation);
    if (position.x <= 17.1f || position.y <= 16)
        throw std::runtime_error("The dynamic body did not hit and slide along the authored slope");

    // Full Cell uses the grid footprint even when its source region is smaller.
    const auto resolveFullCell = [&](const std::string& ref) {
        if (ref == "project-asset://00000003-1111-4111-8111-111111111111")
            return (path.parent_path() / "full-cell.tileset.json").generic_string();
        return resolve(ref);
    };
    sle::entity::Scene fullCellScene;
    const auto wholeCell = sle::SceneLoader::loadData(document, fullCellScene, path.string(), {}, resolveFullCell);
    if (!wholeCell.ok()) throw std::runtime_error(wholeCell.error());
    const auto& fullLayer = fullCellScene.getTileLayers().front();
    if (fullLayer.cellSize != glm::vec2(8) ||
        fullLayer.chunks.at({0, 0}).cells.front().size != glm::vec2(4) ||
        !fullLayer.collisionByTile.at("00000005-1111-4111-8111-111111111111").fullCell)
        throw std::runtime_error("Full Cell did not retain its grid footprint");
}

void checkOpening()
{
    sle::entity::TileLayer layer;
    layer.id = "cutout";
    layer.cellSize = {16, 16};
    layer.collisionByTile["opening"].pieces = {
        {{0, 0}, {4, 0}, {4, 16}, {0, 16}},
        {{12, 0}, {16, 0}, {16, 16}, {12, 16}},
    };
    auto& chunk = layer.chunks[{0, 0}];
    sle::entity::TileCell cell;
    cell.tileId = "opening";
    cell.size = layer.cellSize;
    chunk.cells.push_back(cell);
    chunk.upperRight = cell.size;
    sle::physics::PhysicsWorld world;
    sle::TileCollisionSystem collision(world);
    sle::core::Camera2D camera(32, 32);
    collision.update(std::span(&layer, 1), camera);
    sle::components::RigidBodyComponent dynamic;
    const auto through = world.createBody({8, 21}, dynamic);
    const auto solid = world.createBody({2, 21}, dynamic);
    sle::components::CircleColliderComponent ball;
    ball.radius = 1;
    world.createCircleFixture(through, ball);
    world.createCircleFixture(solid, ball);
    for (int step = 0; step < 25; ++step) world.step(1.0f / 60.0f);
    glm::vec2 position;
    float rotation;
    world.getBodyTransform(through, position, rotation);
    if (position.y >= 0) throw std::runtime_error("The transparent opening blocked the body");
    world.getBodyTransform(solid, position, rotation);
    if (position.y < 17) throw std::runtime_error("The solid side of the opening did not support the body");
}

} // namespace

int main()
try {
    checkAuthoredSlope();
    checkOpening();
    sle::physics::PhysicsWorld world;
    sle::core::Camera2D camera(100, 100);
    sle::entity::TileLayer layer;
    layer.id = "ground";
    layer.cellSize = {8, 8};
    for (int x = 0; x < 64; ++x) {
        auto& chunk = layer.chunks[{x / 32, 0}];
        sle::entity::TileCell cell;
        cell.coordinate = {x, 0};
        cell.lowerLeft = {8 * x, 0};
        cell.size = layer.cellSize;
        cell.tileId = "solid";
        chunk.cells.push_back(cell);
        chunk.lowerLeft = chunk.cells.front().lowerLeft;
        chunk.upperRight = cell.lowerLeft + cell.size;
    }
    layer.collisionByTile["solid"].fullCell = true;
    {
        sle::TileCollisionSystem collision(world);
        const auto sync = [&] { collision.update(std::span(&layer, 1), camera); };
        sync();
        if (world.getRawWorld()->GetBodyCount() != 2 ||
            world.getRawWorld()->GetBodyList()->GetFixtureList()->GetNext())
            throw std::runtime_error("Expected one coalesced fixture per active chunk");
        const auto* previous = world.getRawWorld()->GetBodyList();
        sync();
        if (world.getRawWorld()->GetBodyList() != previous)
            throw std::runtime_error("Unchanged chunk was rebuilt");
        auto& firstCells = layer.chunks.at({0, 0}).cells;
        auto saved = firstCells[10];
        firstCells.erase(firstCells.begin() + 10);
        sync();
        bool keptNeighbor = false;
        for (auto* body = world.getRawWorld()->GetBodyList(); body; body = body->GetNext())
            keptNeighbor |= (body == previous && body->GetPosition().x > 5);
        if (!keptNeighbor || world.getRawWorld()->GetBodyCount() != 2)
            throw std::runtime_error("Rebuilding one chunk changed its neighbor");
        firstCells.insert(firstCells.begin() + 10, std::move(saved));
        sync();
        const auto crossBoundary = [&] {
            camera.setPosition({-120, 0});
            sync();
            sle::components::RigidBodyComponent body;
            body.bodyType = sle::components::BodyType::Dynamic;
            const auto player = world.createBody({230, 20}, body);
            sle::components::CircleColliderComponent circle;
            circle.radius = 4;
            circle.friction = 0;
            world.createCircleFixture(player, circle);
            world.setBodyVelocity(player, {85, 0});
            for (int frame = 0; frame < 75; ++frame) {
                glm::vec2 position;
                float rotation;
                world.getBodyTransform(player, position, rotation);
                camera.setPosition({position.x - 90, 0});
                sync();
                world.step(1.0f / 60.0f);
            }
            glm::vec2 position;
            float rotation;
            world.getBodyTransform(player, position, rotation);
            if (position.x < 310 || position.y < 11 || position.y > 14)
                throw std::runtime_error("Body failed to travel across the chunk boundary");
            world.destroyBody(player);
        };
        crossBoundary();
        layer.collisionByTile["split"].pieces = {
            {{0, 0}, {4, 0}, {4, 8}, {0, 8}},
            {{4, 0}, {8, 0}, {8, 8}, {4, 8}},
        };
        layer.chunks.at({0, 0}).cells.back().tileId = "split";
        layer.chunks.at({1, 0}).cells.front().tileId = "split";
        crossBoundary();
        camera.setPosition({2000, 0});
        sync();
        if (world.getRawWorld()->GetBodyCount()) throw std::runtime_error("Distant chunks kept bodies");
        layer.collisionEnabled = false;
        camera.setPosition({0, 0});
        sync();
        if (world.getRawWorld()->GetBodyCount()) throw std::runtime_error("Disabled collision resumed");
        layer.collisionEnabled = true;
        sync();
        if (world.getRawWorld()->GetBodyCount() != 2) throw std::runtime_error("Chunks failed to resume");
    }
    if (world.getRawWorld()->GetBodyCount()) throw std::runtime_error("Tile bodies survived teardown");
    return 0;
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
