package de.espend.mcp.devkit;

import com.intellij.lang.Language;
import com.intellij.mcpserver.McpToolset;
import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class PsiMcpToolset implements McpToolset {
    @McpTool(name = "psi_structure")
    @McpDescription(description = """
            Parses source code and returns the PSI (Program Structure Interface) tree, which is IntelliJ's internal Abstract Syntax Tree (AST) representation.
           \s
            Use this tool to:
            - Analyze code structure and understand how IntelliJ parses specific syntax
            - Debug or develop IntelliJ plugins that work with PSI elements
            - Inspect the hierarchical node structure of code (classes, methods, expressions, etc.)
            - Understand token types and element relationships for a given language
           \s
            Returns a formatted tree showing PsiElement types, their hierarchy, and text content.
            The language parser is automatically detected based on the provided file extension.
       \s""")
    public Object psi_structure(
        @McpDescription(description = "File extension to determine the language parser (e.g., 'java', 'kt', 'py', 'php', 'twig', 'js', 'ts', 'xml', 'html', 'json')") @NotNull String extension,
        @McpDescription(description = "The source code snippet to parse and generate the PSI tree structure for") @NotNull String content
    ) {
        Language language = getLanguageByExtension(extension);
         if (language == null) {
            return "Error: Could not determine language or plugin for extension: " + extension;
        }

        return ReadAction.compute(() -> {
            Project project = ProjectManager.getInstance().getDefaultProject();
            PsiFile psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(extension, language, content);

            return getPsiData(psiFile);
        });
    }

    @Nullable
    private Language getLanguageByExtension(String extension) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
        if (fileType instanceof LanguageFileType) {
            return ((LanguageFileType) fileType).getLanguage();
        }
        return null;
    }

    @NotNull
    private String getPsiData(PsiFile pf) {
        StringBuilder data = new StringBuilder();
        for (PsiFile file : pf.getViewProvider().getAllFiles()) {
            data.append(DebugUtil.psiToString(file, false, true));
        }
        return data.toString();
    }
}
