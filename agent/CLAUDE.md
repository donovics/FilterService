# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FilterService** is a Java 17 Maven project (`groupId: org.jztrmnkl`, `artifactId: FilterService`, version `1.0-SNAPSHOT`). The project is in early development with only the Maven skeleton in place.

## Build Commands

Run all Maven commands from the project root.

```bash
# Compile
mvn clean compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName

# Package into JAR
mvn clean package

# Show dependency tree
mvn dependency:tree
```

## IDE Setup

The project includes IntelliJ IDEA configuration (`.idea/`). Set the project SDK to Java 17+; UTF-8 encoding is already configured for sources and resources.
