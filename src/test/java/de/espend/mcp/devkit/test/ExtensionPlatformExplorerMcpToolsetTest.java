package de.espend.mcp.devkit.test;

import de.espend.mcp.devkit.ExtensionPlatformExplorerMcpToolset;
import junit.framework.TestCase;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class ExtensionPlatformExplorerMcpToolsetTest extends TestCase {

    private ExtensionPlatformExplorerMcpToolset toolset;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        toolset = new ExtensionPlatformExplorerMcpToolset();
    }

    public void testExtensionListReturnsString() {
        Object result = toolset.extension_list();
        assertNotNull(result);
        assertTrue(result.toString().contains("com.intellij.completion.contributor"));
    }

    public void testExtensionSearchReturnsSet() {
        Object result = toolset.extension_search("completion");
        assertNotNull(result);
        assertTrue(result.toString().contains("com.intellij.completion.contributor"));;
    }

    public void testExtensionSearchWithEmptyQuery() {
        Object result = toolset.extension_search("");
        assertNotNull(result);
        assertTrue(result.toString().contains("error"));
    }

    public void testExtensionDetailWithUnknownExtension() {
        Object result = toolset.extension_detail("com.unknown.nonexistent.extension");
        assertNotNull(result);
        assertTrue(result instanceof String);
        String content = (String) result;
        assertTrue(content.contains("not found") || content.isEmpty() || content.contains("Extension point"));
    }

    public void testExtensionDetailReturnsString() {
        Object result = toolset.extension_detail("com.intellij.completion.contributor");
        assertNotNull(result);
        assertTrue(result.toString().contains("https://github.com/search"));
    }

    public void testExtensionSearchCaseInsensitive() {
        Object resultLower = toolset.extension_search("completion");
        Object resultUpper = toolset.extension_search("COMPLETION");
        assertNotNull(resultLower);
        assertNotNull(resultUpper);
        assertTrue(resultLower.toString().contains("com.intellij.completion.contributor"));;
        assertTrue(resultUpper.toString().contains("com.intellij.completion.contributor"));;
    }
}
