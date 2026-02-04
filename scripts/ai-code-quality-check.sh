#!/bin/bash
#
# AI Code Quality Checker
# Detects common mistakes in AI-generated Java code
#
# Usage: ./scripts/ai-code-quality-check.sh [--strict]
#
# Based on patterns from CLAUDE.md coding standards
#

set -e

STRICT_MODE=false
if [ "$1" == "--strict" ]; then
    STRICT_MODE=true
fi

ERRORS=0
WARNINGS=0

echo "╔════════════════════════════════════════════════════════════╗"
echo "║           AI Code Quality Checker v1.0                     ║"
echo "║   Detecting common AI-generated code mistakes              ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

SRC_DIR="ai2qa-*/src/main/java"

# Helper function
check_pattern() {
    local pattern="$1"
    local message="$2"
    local severity="$3"  # ERROR or WARNING
    local include="$4"

    local matches=$(grep -rn "$pattern" --include="$include" $SRC_DIR 2>/dev/null | grep -v "// OK:" | grep -v "// IGNORE:" || true)

    if [ -n "$matches" ]; then
        if [ "$severity" == "ERROR" ]; then
            echo "❌ ERROR: $message"
            ((ERRORS++))
        else
            echo "⚠️  WARNING: $message"
            ((WARNINGS++))
        fi
        echo "$matches" | head -10
        echo ""
    fi
}

echo "=== Checking for Prohibited Patterns ==="
echo ""

# 1. Lombok annotations (prohibited per CLAUDE.md)
check_pattern "@Slf4j" "Lombok @Slf4j annotation found. Use manual Logger initialization." "ERROR" "*.java"
check_pattern "@Data" "Lombok @Data annotation found. Use Java Records or manual getters." "ERROR" "*.java"
check_pattern "@AllArgsConstructor" "Lombok @AllArgsConstructor found. Use explicit constructors." "ERROR" "*.java"
check_pattern "@NoArgsConstructor" "Lombok @NoArgsConstructor found. Use explicit constructors." "ERROR" "*.java"
check_pattern "@Builder" "Lombok @Builder found. Implement builder pattern manually." "WARNING" "*.java"

# 2. Null returns (should use Optional)
check_pattern "return null;" "Returning null instead of Optional<T>." "ERROR" "*.java"

# 3. Object as type (type safety issue)
check_pattern "Object [a-z][a-zA-Z]* =" "Using 'Object' as variable type. Use specific types." "ERROR" "*.java"
check_pattern "Map<String, Object>" "Using Object in Map. Consider typed DTOs or records." "WARNING" "*.java"

# 4. Checked exceptions in domain (clean architecture violation)
check_pattern "throws IOException" "Checked exception in signature. Wrap in unchecked exception." "WARNING" "*.java"
check_pattern "throws SQLException" "SQLException in signature. Should be wrapped by repository." "ERROR" "*.java"

# 5. LocalDateTime instead of Instant (timezone issues)
check_pattern "LocalDateTime\\.now()" "Using LocalDateTime.now(). Use Instant.now() for UTC consistency." "WARNING" "*.java"
check_pattern "LocalDateTime [a-z]" "LocalDateTime field found. Consider using Instant for timestamps." "WARNING" "*.java"

# 6. Validation in record constructors (clean architecture violation)
echo "=== Checking for Validation in Records ==="
echo ""
# This is harder to detect with grep, so we check for common patterns
check_pattern "public record.*\\{[^}]*if (" "Possible validation logic in record. Move to Factory class." "WARNING" "*.java"
check_pattern "public record.*\\{[^}]*throw " "Exception throwing in record. Move validation to Factory." "ERROR" "*.java"

# 7. Missing final keywords on fields
echo "=== Checking for Immutability Issues ==="
echo ""
check_pattern "private [A-Z][a-zA-Z<>]*[^f][^i][^n][^a][^l] [a-z]" "Non-final private field. Consider making immutable." "WARNING" "*.java"

# 8. Empty catch blocks (swallowing exceptions)
echo "=== Checking for Exception Handling Issues ==="
echo ""
check_pattern "catch.*\\{[[:space:]]*\\}" "Empty catch block. Handle or rethrow exceptions." "ERROR" "*.java"
check_pattern "catch.*\\{ *\\/\\/" "Catch block with only comment. Handle or rethrow exceptions." "WARNING" "*.java"

# 9. System.out/err usage (should use logger)
echo "=== Checking for Logging Issues ==="
echo ""
check_pattern "System\\.out\\.print" "Using System.out. Use SLF4J logger instead." "ERROR" "*.java"
check_pattern "System\\.err\\.print" "Using System.err. Use logger.error() instead." "ERROR" "*.java"
check_pattern "printStackTrace()" "Using printStackTrace(). Use logger.error(msg, e) instead." "ERROR" "*.java"

# 10. Hardcoded secrets (security issue)
echo "=== Checking for Security Issues ==="
echo ""
check_pattern "password.*=.*\"" "Possible hardcoded password. Use environment variables." "ERROR" "*.java"
check_pattern "apiKey.*=.*\"" "Possible hardcoded API key. Use environment variables." "ERROR" "*.java"
check_pattern "secret.*=.*\"[^\"]+\"" "Possible hardcoded secret. Use environment variables." "ERROR" "*.java"

# 11. Thread.sleep in production code (usually wrong)
check_pattern "Thread\\.sleep" "Thread.sleep() found. Consider async patterns or proper scheduling." "WARNING" "*.java"

# 12. Raw types (generics without type parameters)
echo "=== Checking for Type Safety Issues ==="
echo ""
check_pattern "List [a-z]" "Raw List type. Use List<T> with type parameter." "WARNING" "*.java"
check_pattern "Map [a-z]" "Raw Map type. Use Map<K,V> with type parameters." "WARNING" "*.java"
check_pattern "Set [a-z]" "Raw Set type. Use Set<T> with type parameter." "WARNING" "*.java"

# Summary
echo ""
echo "════════════════════════════════════════════════════════════"
echo "                      SUMMARY                               "
echo "════════════════════════════════════════════════════════════"
echo ""
echo "  Errors:   $ERRORS"
echo "  Warnings: $WARNINGS"
echo ""

if [ $ERRORS -gt 0 ]; then
    echo "❌ Code quality check FAILED with $ERRORS errors."
    if [ "$STRICT_MODE" == true ]; then
        exit 1
    fi
elif [ $WARNINGS -gt 0 ]; then
    echo "⚠️  Code quality check passed with $WARNINGS warnings."
else
    echo "✅ Code quality check PASSED. No issues found."
fi
