package com.github.dcysteine.nesql.exporter.util.render;

public final class RuntimeFieldResolverTest {
    private RuntimeFieldResolverTest() {}

    public static void main(String[] args) {
        ChildFixture fixture = new ChildFixture();
        require(
                "mcp-value".equals(RuntimeFieldResolver.read(fixture, "mcpField", "field_mcp")),
                "MCP alias should resolve first");
        require(
                "srg-value".equals(RuntimeFieldResolver.read(fixture, "missingMcp", "field_srg")),
                "SRG alias should resolve inherited production field");
        require(
                "srg-value".equals(RuntimeFieldResolver.read(fixture, "missingMcp", "field_srg")),
                "cached SRG field should remain readable");
        require(
                RuntimeFieldResolver.read(fixture, "missingOne", "missingTwo") == null,
                "missing aliases should return null");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class BaseFixture {
        @SuppressWarnings("unused")
        private final String field_srg = "srg-value";
    }

    private static final class ChildFixture extends BaseFixture {
        @SuppressWarnings("unused")
        private final String mcpField = "mcp-value";
    }
}
