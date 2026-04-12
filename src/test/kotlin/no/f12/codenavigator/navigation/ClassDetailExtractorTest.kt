package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.classinfo.ClassDetailExtractor
import no.f12.codenavigator.navigation.classinfo.FieldDetail
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassDetailExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts class name and source file`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.MyService", detail.className.value)
        assertEquals("MyService.kt", detail.sourceFile)
    }

    // [TEST-DONE] Extracts class name and source file
    @Test
    fun `extracts superclass name when not Object`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/AdminService", "AdminService.kt",
            superName = "com/example/BaseService",
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.BaseService", detail.superClass?.value)
    }

    // [TEST-DONE] Extracts superclass name (non-Object)
    @Test
    fun `extracts implemented interfaces`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyRepo", "MyRepo.kt",
            interfaces = arrayOf("com/example/Repository", "java/io/Serializable"),
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("com.example.Repository", "java.io.Serializable"), detail.interfaces.map { it.value })
    }

    // [TEST-DONE] Extracts implemented interfaces
    @Test
    fun `extracts public methods with parameter types and return type`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Service", "Service.kt") {
            visitMethod(
                Opcodes.ACC_PUBLIC,
                "findUser",
                "(Ljava/lang/String;I)Lcom/example/User;",
                null,
                null,
            )
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        val method = detail.methods.first()
        assertEquals("findUser", method.name)
        assertEquals(listOf("String", "int"), method.parameterTypes)
        assertEquals("User", method.returnType)
    }

    // [TEST-DONE] Extracts public methods with parameter types and return type
    @Test
    fun `extracts public fields with type`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Config", "Config.kt") {
            visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
            visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null)
            visitField(Opcodes.ACC_PUBLIC, "items", "[Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(3, detail.fields.size)
        assertEquals(FieldDetail("name", "String", emptyList()), detail.fields[0])
        assertEquals(FieldDetail("count", "int", emptyList()), detail.fields[1])
        assertEquals(FieldDetail("items", "String[]", emptyList()), detail.fields[2])
    }

    // [TEST-DONE] Extracts public fields with type
    @Test
    fun `filters out constructors and synthetic methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC, "bridge\$method", "()V", null, null)
            visitMethod(Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, "access\$getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        assertEquals("doWork", detail.methods.first().name)
    }

    // [TEST-DONE] Filters out constructors and synthetic methods
    @Test
    fun `filters out Kotlin property accessors for fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/User", "User.kt") {
            visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "setName", "(Ljava/lang/String;)V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("name"), detail.fields.map { it.name })
        assertEquals(listOf("doSomething"), detail.methods.map { it.name })
    }

    // [TEST-DONE] Filters out Kotlin property accessors for fields
    @Test
    fun `filters out data class generated methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Data", "Data.kt") {
            visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "component1", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy", "(Ljava/lang/String;)Lcom/example/Data;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy\$default", "(Lcom/example/Data;Ljava/lang/String;ILjava/lang/Object;)Lcom/example/Data;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "transform", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("transform"), detail.methods.map { it.name })
    }

    // [TEST-DONE] Filters out data class generated methods
    @Test
    fun `default superclass Object is shown as null`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Simple", "Simple.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(null, detail.superClass)
    }

    // [TEST-DONE] Default superclass is Object (shown as empty/null)

    @Test
    fun `class with no interfaces has empty list`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Plain", "Plain.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertTrue(detail.interfaces.isEmpty())
    }

    // [TEST-DONE] Class with no interfaces has empty list

    @Test
    fun `INSTANCE field is excluded from fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Singleton", "Singleton.kt") {
            visitField(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "INSTANCE",
                "Lcom/example/Singleton;",
                null,
                null,
            )
            visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("name"), detail.fields.map { it.name })
    }

    @Test
    fun `is-prefix accessor for boolean field is filtered from methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Flags", "Flags.kt") {
            visitField(Opcodes.ACC_PRIVATE, "active", "Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "isActive", "()Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "toggle", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("active"), detail.fields.map { it.name })
        assertEquals(listOf("toggle"), detail.methods.map { it.name })
    }

    @Test
    fun `simplifyType handles all primitive types`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Primitives", "Primitives.kt") {
            visitField(Opcodes.ACC_PUBLIC, "flag", "Z", null, null)
            visitField(Opcodes.ACC_PUBLIC, "letter", "C", null, null)
            visitField(Opcodes.ACC_PUBLIC, "tiny", "B", null, null)
            visitField(Opcodes.ACC_PUBLIC, "small", "S", null, null)
            visitField(Opcodes.ACC_PUBLIC, "ratio", "F", null, null)
            visitField(Opcodes.ACC_PUBLIC, "big", "J", null, null)
            visitField(Opcodes.ACC_PUBLIC, "precise", "D", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val fieldsByName = detail.fields.associateBy { it.name }
        assertEquals("boolean", fieldsByName["flag"]?.type)
        assertEquals("char", fieldsByName["letter"]?.type)
        assertEquals("byte", fieldsByName["tiny"]?.type)
        assertEquals("short", fieldsByName["small"]?.type)
        assertEquals("float", fieldsByName["ratio"]?.type)
        assertEquals("long", fieldsByName["big"]?.type)
        assertEquals("double", fieldsByName["precise"]?.type)
    }

    @Test
    fun `sourceFile defaults to unknown when no source is provided`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/NoSource", null)

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("<unknown>", detail.sourceFile)
    }

    @Test
    fun `synthetic fields are excluded`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSynthetic", "WithSynthetic.kt") {
            visitField(Opcodes.ACC_SYNTHETIC, "\$\$delegatedProperties", "[Ljava/lang/Object;", null, null)
            visitField(Opcodes.ACC_PUBLIC, "realField", "Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("realField"), detail.fields.map { it.name })
    }


    @Test
    fun `extracts annotation with multiple parameters`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Resilient", "Resilient.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "callService", "()V", null, null)
            val av = mv.visitAnnotation("Lio/github/resilience4j/circuitbreaker/annotation/CircuitBreaker;", true)
            av?.visit("name", "backend")
            av?.visit("fallbackMethod", "fallback")
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(AnnotationName("io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker"), annotation.name)
        assertEquals(mapOf("name" to "backend", "fallbackMethod" to "fallback"), annotation.parameters)
    }

    @Test
    fun `extracts annotation with string parameter`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Cached", "Cached.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "getUser", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/cache/annotation/Cacheable;", true)
            av?.visit("value", "users")
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(AnnotationName("org.springframework.cache.annotation.Cacheable"), annotation.name)
        assertEquals(mapOf("value" to "users"), annotation.parameters)
    }

    @Test
    fun `extracts field-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithFieldAnnotation", "WithFieldAnnotation.kt") {
            val fv = visitField(Opcodes.ACC_PUBLIC, "userRepo", "Ljava/lang/Object;", null, null)
            fv.visitAnnotation("Ljakarta/inject/Inject;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.fields.size)
        assertEquals(1, detail.fields.first().annotations.size)
        assertEquals(AnnotationName("jakarta.inject.Inject"), detail.fields.first().annotations.first().name)
    }

    @Test
    fun `extracts multiple class-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MultiAnnotated", "MultiAnnotated.kt") {
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)?.visitEnd()
            visitAnnotation("Lorg/springframework/transaction/annotation/Transactional;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(2, detail.annotations.size)
        assertEquals(
            listOf(
                AnnotationName("org.springframework.stereotype.Service"),
                AnnotationName("org.springframework.transaction.annotation.Transactional"),
            ),
            detail.annotations.map { it.name },
        )
    }

    @Test
    fun `extracts method-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithMethodAnnotation", "WithMethodAnnotation.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "resilientCall", "()V", null, null)
            mv.visitAnnotation("Lio/github/resilience4j/circuitbreaker/annotation/CircuitBreaker;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        assertEquals(1, detail.methods.first().annotations.size)
        assertEquals(
            AnnotationName("io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker"),
            detail.methods.first().annotations.first().name,
        )
    }

    @Test
    fun `extracts single class-level annotation`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Annotated", "Annotated.kt") {
            visitAnnotation("Lorg/springframework/stereotype/Component;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.annotations.size)
        assertEquals(AnnotationName("org.springframework.stereotype.Component"), detail.annotations.first().name)
    }

    @Test
    fun `class with no annotations has empty annotation lists`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Plain", "Plain.kt") {
            visitField(Opcodes.ACC_PUBLIC, "value", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertTrue(detail.annotations.isEmpty())
        assertTrue(detail.fields.first().annotations.isEmpty())
        assertTrue(detail.methods.first().annotations.isEmpty())
    }

    // [TEST] Enum annotation parameter is extracted with simple class and constant name
    @Test
    fun `nested annotation parameter is extracted with at-sign prefix and parameters`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithNested", "WithNested.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lio/swagger/v3/oas/annotations/Operation;", true)
            val nestedAv = av?.visitAnnotation("summary", "Lio/swagger/v3/oas/annotations/responses/ApiResponse;")
            nestedAv?.visit("responseCode", "200")
            nestedAv?.visit("description", "OK")
            nestedAv?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(AnnotationName("io.swagger.v3.oas.annotations.Operation"), annotation.name)
        assertEquals(mapOf("summary" to "@ApiResponse(responseCode=200, description=OK)"), annotation.parameters)
    }

    @Test
    fun `array annotation parameter with multiple values is extracted as bracket-delimited list`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithArray", "WithArray.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            val arrayVisitor = av?.visitArray("value")
            arrayVisitor?.visit(null, "/api/users")
            arrayVisitor?.visit(null, "/api/v2/users")
            arrayVisitor?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(AnnotationName("org.springframework.web.bind.annotation.RequestMapping"), annotation.name)
        assertEquals(mapOf("value" to "[/api/users, /api/v2/users]"), annotation.parameters)
    }

    @Test
    fun `array annotation parameter with single value is extracted without brackets`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSingleArray", "WithSingleArray.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            val arrayVisitor = av?.visitArray("value")
            arrayVisitor?.visit(null, "/api/users")
            arrayVisitor?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(mapOf("value" to "/api/users"), annotation.parameters)
    }

    @Test
    fun `empty array annotation parameter is extracted as empty brackets`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithEmptyArray", "WithEmptyArray.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            val arrayVisitor = av?.visitArray("produces")
            arrayVisitor?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(mapOf("produces" to "[]"), annotation.parameters)
    }

    @Test
    fun `array of enum values is extracted as bracket-delimited list of enum constants`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithEnumArray", "WithEnumArray.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            val arrayVisitor = av?.visitArray("method")
            arrayVisitor?.visitEnum(null, "Lorg/springframework/web/bind/annotation/RequestMethod;", "GET")
            arrayVisitor?.visitEnum(null, "Lorg/springframework/web/bind/annotation/RequestMethod;", "POST")
            arrayVisitor?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(mapOf("method" to "[RequestMethod.GET, RequestMethod.POST]"), annotation.parameters)
    }

    @Test
    fun `enum annotation parameter is extracted with class and constant name`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithEnum", "WithEnum.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/RequestMapping;", true)
            av?.visitEnum("method", "Lorg/springframework/web/bind/annotation/RequestMethod;", "GET")
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(AnnotationName("org.springframework.web.bind.annotation.RequestMapping"), annotation.name)
        assertEquals(mapOf("method" to "RequestMethod.GET"), annotation.parameters)
    }

    @Test
    fun `array of nested annotations is extracted with at-sign prefix`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithJoinTable", "WithJoinTable.kt") {
            val fv = visitField(Opcodes.ACC_PUBLIC, "tags", "Ljava/util/Set;", null, null)
            val av = fv.visitAnnotation("Ljakarta/persistence/JoinTable;", true)
            av?.visit("name", "articles_tags")
            val joinColArray = av?.visitArray("joinColumns")
            val jc1 = joinColArray?.visitAnnotation(null, "Ljakarta/persistence/JoinColumn;")
            jc1?.visit("name", "article_id")
            jc1?.visitEnd()
            joinColArray?.visitEnd()
            val invArray = av?.visitArray("inverseJoinColumns")
            val jc2 = invArray?.visitAnnotation(null, "Ljakarta/persistence/JoinColumn;")
            jc2?.visit("name", "tag_id")
            jc2?.visitEnd()
            invArray?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.fields.first().annotations.first()
        assertEquals(AnnotationName("jakarta.persistence.JoinTable"), annotation.name)
        assertEquals("articles_tags", annotation.parameters["name"])
        assertEquals("@JoinColumn(name=article_id)", annotation.parameters["joinColumns"])
        assertEquals("@JoinColumn(name=tag_id)", annotation.parameters["inverseJoinColumns"])
    }

    @Test
    fun `array of multiple nested annotations renders all items`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSpec", "WithSpec.kt") {
            val av = visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/And;", true)
            val specArray = av?.visitArray("value")
            val spec1 = specArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Spec;")
            spec1?.visit("path", "author.username")
            spec1?.visit("params", "author")
            spec1?.visitEnd()
            val spec2 = specArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Spec;")
            spec2?.visit("path", "tags.name")
            spec2?.visit("params", "tag")
            spec2?.visitEnd()
            specArray?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.annotations.first()
        assertEquals(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.And"), annotation.name)
        assertEquals("[@Spec(path=author.username, params=author), @Spec(path=tags.name, params=tag)]", annotation.parameters["value"])
    }

    @Test
    fun `class literal annotation parameter is rendered as simple name`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithClassLiteral", "WithClassLiteral.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/ExceptionHandler;", true)
            av?.visit("value", Type.getType("Lcom/example/NotFoundException;"))
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(mapOf("value" to "NotFoundException"), annotation.parameters)
    }

    @Test
    fun `class literal in annotation array is rendered as simple name`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithClassArray", "WithClassArray.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "handle", "()V", null, null)
            val av = mv.visitAnnotation("Lorg/springframework/web/bind/annotation/ExceptionHandler;", true)
            val arrayVisitor = av?.visitArray("value")
            arrayVisitor?.visit(null, Type.getType("Lcom/example/NotFoundException;"))
            arrayVisitor?.visit(null, Type.getType("Lcom/example/BadRequestException;"))
            arrayVisitor?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.methods.first().annotations.first()
        assertEquals(mapOf("value" to "[NotFoundException, BadRequestException]"), annotation.parameters)
    }

    @Test
    fun `class literal inside nested annotation in array is rendered as simple name`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSpecClass", "WithSpecClass.kt") {
            val av = visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/And;", true)
            val specArray = av?.visitArray("value")
            val spec1 = specArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Spec;")
            spec1?.visit("path", "author.username")
            spec1?.visit("spec", Type.getType("Lnet/kaczmarzyk/spring/data/jpa/domain/Like;"))
            spec1?.visitEnd()
            specArray?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val annotation = detail.annotations.first()
        assertEquals("@Spec(path=author.username, spec=Like)", annotation.parameters["value"])
    }

    @Test
    fun `repeatable annotation container is unwrapped into individual annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithJoins", "WithJoins.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "findAll", "()V", null, null)
            val container = mv.visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/RepeatedJoin;", true)
            val valueArray = container?.visitArray("value")
            val join1 = valueArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Join;")
            join1?.visit("path", "author")
            join1?.visit("alias", "a")
            join1?.visitEnd()
            val join2 = valueArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Join;")
            join2?.visit("path", "tags")
            join2?.visit("alias", "t")
            join2?.visitEnd()
            valueArray?.visitEnd()
            container?.visitEnd()
            mv.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val method = detail.methods.first()
        assertEquals(2, method.annotations.size)
        assertEquals(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.Join"), method.annotations[0].name)
        assertEquals(mapOf("path" to "author", "alias" to "a"), method.annotations[0].parameters)
        assertEquals(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.Join"), method.annotations[1].name)
        assertEquals(mapOf("path" to "tags", "alias" to "t"), method.annotations[1].parameters)
    }

    @Test
    fun `repeatable annotation container on class is unwrapped`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithPropertySources", "WithPropertySources.kt") {
            val container = visitAnnotation("Lorg/springframework/context/annotation/PropertySources;", true)
            val valueArray = container?.visitArray("value")
            val ps1 = valueArray?.visitAnnotation(null, "Lorg/springframework/context/annotation/PropertySource;")
            ps1?.visit("value", "classpath:app.properties")
            ps1?.visitEnd()
            val ps2 = valueArray?.visitAnnotation(null, "Lorg/springframework/context/annotation/PropertySource;")
            ps2?.visit("value", "classpath:db.properties")
            ps2?.visitEnd()
            valueArray?.visitEnd()
            container?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(2, detail.annotations.size)
        assertEquals(AnnotationName("org.springframework.context.annotation.PropertySource"), detail.annotations[0].name)
        assertEquals(mapOf("value" to "classpath:app.properties"), detail.annotations[0].parameters)
        assertEquals(AnnotationName("org.springframework.context.annotation.PropertySource"), detail.annotations[1].name)
        assertEquals(mapOf("value" to "classpath:db.properties"), detail.annotations[1].parameters)
    }

    @Test
    fun `non-repeatable annotation with same-type nested value array is not unwrapped`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithAnd", "WithAnd.kt") {
            val av = visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/And;", true)
            val specArray = av?.visitArray("value")
            val spec1 = specArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Spec;")
            spec1?.visit("path", "name")
            spec1?.visitEnd()
            val spec2 = specArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Spec;")
            spec2?.visit("path", "email")
            spec2?.visitEnd()
            specArray?.visitEnd()
            av?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.annotations.size)
        assertEquals(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.And"), detail.annotations.first().name)
    }

    @Test
    fun `single repeatable annotation in container is unwrapped`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSingleJoin", "WithSingleJoin.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "find", "()V", null, null)
            val container = mv.visitAnnotation("Lnet/kaczmarzyk/spring/data/jpa/web/annotation/RepeatedJoin;", true)
            val valueArray = container?.visitArray("value")
            val join = valueArray?.visitAnnotation(null, "Lnet/kaczmarzyk/spring/data/jpa/web/annotation/Join;")
            join?.visit("path", "author")
            join?.visitEnd()
            valueArray?.visitEnd()
            container?.visitEnd()
            mv.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val method = detail.methods.first()
        assertEquals(1, method.annotations.size)
        assertEquals(AnnotationName("net.kaczmarzyk.spring.data.jpa.web.annotation.Join"), method.annotations[0].name)
        assertEquals(mapOf("path" to "author"), method.annotations[0].parameters)
    }

    @Test
    fun `method with return type using array of primitives`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/ArrayMethods", "ArrayMethods.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "getIds", "()[I", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        assertEquals("int[]", detail.methods.first().returnType)
    }

    @Test
    fun `filters out Kotlin internal annotations from class-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/KotlinClass", "KotlinClass.kt") {
            visitAnnotation("Lkotlin/Metadata;", true)?.visitEnd()
            visitAnnotation("Lkotlin/jvm/internal/SourceDebugExtension;", true)?.visitEnd()
            visitAnnotation("Lorg/springframework/stereotype/Service;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.annotations.size)
        assertEquals(AnnotationName("org.springframework.stereotype.Service"), detail.annotations.first().name)
    }

    @Test
    fun `filters out Kotlin internal annotations from method-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Suspending", "Suspending.kt") {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
            mv.visitAnnotation("Lkotlin/coroutines/jvm/internal/DebugMetadata;", true)?.visitEnd()
            mv.visitAnnotation("Lorg/springframework/scheduling/annotation/Scheduled;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.first().annotations.size)
        assertEquals(
            AnnotationName("org.springframework.scheduling.annotation.Scheduled"),
            detail.methods.first().annotations.first().name,
        )
    }

    @Test
    fun `extracts class detail from ByteArray`() {
        val bytes = syntheticClassBytes("com/example/JarDetail", "JarDetail.kt")

        val detail = ClassDetailExtractor.extract(bytes)

        assertEquals("com.example.JarDetail", detail.className.value)
        assertEquals("JarDetail.kt", detail.sourceFile)
    }

    @Test
    fun `filters out Kotlin internal annotations from field-level annotations`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithFieldMeta", "WithFieldMeta.kt") {
            val fv = visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
            fv.visitAnnotation("Lkotlin/Metadata;", true)?.visitEnd()
            fv.visitAnnotation("Ljakarta/inject/Inject;", true)?.visitEnd()
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.fields.first().annotations.size)
        assertEquals(AnnotationName("jakarta.inject.Inject"), detail.fields.first().annotations.first().name)
    }

    private fun syntheticClassBytes(className: String, sourceFile: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitEnd()
        return writer.toByteArray()
    }
}
