# MCP Devkit

IntelliJ plugin that extends the [MCP Server](https://plugins.jetbrains.com/plugin/26071-mcp-server) plugin with tools for IntelliJ plugin development.

## Features

- **psi_structure** - Parse any code snippet and retrieve the PSI (Program Structure Interface) tree, which is IntelliJ's internal AST representation. Useful for developing and debugging IntelliJ plugins.

## Requirements

- [MCP Server](https://plugins.jetbrains.com/plugin/26071-mcp-server) plugin installed
- Language plugins for the languages you want to analyze (e.g., PHP, Python, Kotlin)

## Setup

Configure MCP in your AI client following the [JetBrains MCP Server documentation](https://www.jetbrains.com/help/idea/mcp-server.html).

The MCP server URL can be found in **Tools | MCP Server** settings (e.g., `http://127.0.0.1:64342/sse`).

## Debugging

Use the [MCP Inspector](https://github.com/modelcontextprotocol/inspector) to debug and test MCP tool calls.

## MCP Tools

### psi_structure

Parses any code snippet and returns the PSI tree structure.

**Parameters:**
- `extension` - File extension to determine the language parser (e.g., `java`, `kt`, `py`, `php`)
- `content` - The source code snippet to parse

**Example use cases:**
- Analyze how IntelliJ parses specific syntax
- Debug IntelliJ plugins that work with PSI elements
- Understand token types and element relationships
