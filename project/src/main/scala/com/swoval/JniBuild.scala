package com.swoval

import java.nio.file.{ Path, Paths }
import java.util.concurrent.TimeUnit

import com.swoval.make._
import sbt._
import sbt.Keys._
import sjsonnew.BasicJsonProtocol._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Properties

object JniBuild {
  private val makeBuildDir = settingKey[Path]("make build directory").withRank(Int.MaxValue)
  private val jniInclude = taskKey[String]("the jni include directory")
  private def getProcOutput(args: String*): String = {
    val proc = new ProcessBuilder(args: _*).start()
    val out = new java.util.Vector[Byte]
    val err = new java.util.Vector[Byte]
    val is = proc.getInputStream
    val es = proc.getErrorStream
    def drain(): Unit = {
      while (is.available > 0) out.add((is.read() & 0xFF).toByte)
      while (es.available > 0) err.add((es.read() & 0xFF).toByte)
    }
    val thread = new Thread() {
      setDaemon(true)
      start()
      @tailrec
      override def run(): Unit = {
        drain()
        if (proc.isAlive && !Thread.currentThread.isInterrupted) {
          try Thread.sleep(10)
          catch { case _: InterruptedException => }
          run()
        }
      }
    }
    proc.waitFor(5, TimeUnit.SECONDS)
    thread.interrupt()
    drain()
    if (!err.isEmpty) System.err.println(new String(err.asScala.toArray))
    new String(out.asScala.toArray)
  }
  private def parentPath(args: String*)(cond: String => Boolean): Option[Path] =
    getProcOutput(args: _*).linesIterator.collectFirst {
      case l if cond(l) => Paths.get(l).getParent
    }
  private val cc = taskKey[String]("compiler")
  private val ccFlags = taskKey[String]("compiler flags")
  val makeSettings = Def.settings(
    makeBuildDir := target.value.toPath / "jni" / "build",
    jniInclude := {
      jniInclude.previous.getOrElse {
        System.getProperty("java8.home") match {
          case null =>
            if (Properties.isMac) {
              parentPath("mdfind", "-name", "jni.h")(_.contains("jdk1.8"))
                .map(p => s"-I$p -I$p/darwin")
                .getOrElse {
                  throw new IllegalStateException("Couldn't find jni.h for jdk 8")
                }
            } else {
              parentPath("locate", "jni.h")(_ => true).map(p => s"-I$p -I$p/linux").getOrElse {
                throw new IllegalStateException("Couldn't find jni.h for jdk 8")
              }
            }
          case h =>
            val platform = if (Properties.isMac) "darwin" else "linux"
            s"-I$h/include/ -i$h/include/$platform"
        }
      }
    },
    "CCX" := { if (Properties.isMac) "clang" else "gcc" },
    Global / cc := { if (Properties.isMac) "clang" else "gcc" },
    Global / ccFlags := m"-Ifiles/jvm/src/main/native/include $jniInclude -Wno-unused-command-line-argument -std=c++11 -O3",
    pat"$makeBuildDir/objects/apple/%.o" :- pat"files/jvm/src/main/native/apple/%.cc" build {
      println(m"${"CCX"}")
      println("---")
      sh(m"${Global / cc} ${Global / ccFlags} -c ${`$^`} -framework Carbon -o ${`$@`}")
    },
//    jniInclude build {
//      jniInclude.track
//    },
    p"$makeBuildDir/foo" :- pat"$makeBuildDir/objects/apple/%.o" build {
      println(p"files/jvm/src/main/native/apple".toAbsolutePath)
      println(Option(p"src/main/native/apple".toFile.list()).toVector)
    }
  )
//  buildNative := {
//    val log = state.value.log
//    val nativeDir = sourceDirectory.value.toPath.resolve("main/native").toFile
//    val makeCmd = System.getProperty("swoval.make.cmd", "make")
//    val proc = new ProcessBuilder(makeCmd, "-j", "8").directory(nativeDir).start()
//    proc.waitFor(1, TimeUnit.MINUTES)
//    log.info(Source.fromInputStream(proc.getInputStream).mkString)
//    if (proc.exitValue() != 0) {
//      log.error(Source.fromInputStream(proc.getErrorStream).mkString)
//      throw new IllegalStateException("Couldn't build native library!")
//    }
//  },
}

//TARGET_DIR := ../../../target
//BUILD_DIR := $(TARGET_DIR)/build
//NATIVE_DIR := native/$(shell uname -sm | cut -d ' ' -f2 | tr '[:upper:]' '[:lower:]')
//LIB_DIR := ../resources/$(NATIVE_DIR)
//LIB_NAME := swoval-files0
//POSIX_LIB_NAME := lib$(LIB_NAME)
//QUICKLIST_SOURCE := com_swoval_files_NativeDirectoryLister
//WIN64CC := x86_64-w64-mingw32-g++
//CC := clang
//
//UNAME_S := $(shell uname -s)
//
//ifeq ($(UNAME_S),Darwin)
//JNI_INCLUDE := -I$(shell mdfind -name jni.h | grep jdk1.8 | tail -n 1 | xargs dirname)\
//-I$(shell mdfind -name jni_md.h | grep jdk1.8 | tail -n 1 | xargs dirname)
//LD := /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++
//OBJS := $(BUILD_DIR)/x86_64/darwin/$(QUICKLIST_SOURCE).o \
//$(BUILD_DIR)/x86_64/darwin/com_swoval_files_FileEventMonitorImpl.o \
//$(BUILD_DIR)/x86_64/windows/$(QUICKLIST_SOURCE).o \
//
//LIBS := $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib \
//$(TARGET_DIR)/x86_64/$(LIB_NAME).dll \
//
//
//endif
//
//ifeq ($(UNAME_S), Linux)
//BASE_INCLUDE := $(shell locate jni.h | tail -n 1 | xargs dirname)
//JNI_INCLUDE := -I$(BASE_INCLUDE) -I$(BASE_INCLUDE)/linux
//OBJS := $(BUILD_DIR)/x86_64/linux/$(QUICKLIST_SOURCE).o \
//
//LIBS := $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so \
//
//endif
//
//
//ifeq ($(UNAME_S), FreeBSD)
//JNI_INCLUDE := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/freebsd
//OBJS := $(BUILD_DIR)/x86_64/freebsd/$(QUICKLIST_SOURCE).o \
//
//LIBS := $(TARGET_DIR)/x86_64/freebsd/$(POSIX_LIB_NAME).so \
//
//endif
//
//LINUX_CCFLAGS := -fPIC
//LINUX_LDFLAGS := -fPIC -shared
//
//NPMDIR := ../../../../js/npm/src/
//CCFLAGS := -I./include -I$(NPMDIR) $(JNI_INCLUDE) -Wno-unused-command-line-argument -std=c++11 -O3
//
//all: $(LIBS)
//
//.PHONY: clean all
//
//$(BUILD_DIR)/x86_64/linux/$(QUICKLIST_SOURCE).o: posix/$(QUICKLIST_SOURCE).cc
//echo $(JNI_INCLUDE)
//mkdir -p $(BUILD_DIR)/x86_64/linux; \
//$(CC) -c $< $(CCFLAGS) $(JNI_INCLUDE) -fPIC -o $@
//
//$(BUILD_DIR)/x86_64/freebsd/$(QUICKLIST_SOURCE).o: posix/$(QUICKLIST_SOURCE).cc
//mkdir -p $(BUILD_DIR)/x86_64/freebsd; \
//$(CC) -c $< $(CCFLAGS) $(JNI_INCLUDE) -fPIC -o $@
//
//$(BUILD_DIR)/x86_64/windows/$(QUICKLIST_SOURCE).o: windows/$(QUICKLIST_SOURCE).cc
//mkdir -p $(BUILD_DIR)/x86_64/windows; \
//$(WIN64CC) -c $< $(CCFLAGS) -o $@ -D__WIN__
//
//$(BUILD_DIR)/x86_64/darwin/$(QUICKLIST_SOURCE).o: posix/$(QUICKLIST_SOURCE).cc
//mkdir -p $(BUILD_DIR)/x86_64/darwin; \
//$(CC) -c $< $(CCFLAGS) -framework Carbon -o $@
//
//$(BUILD_DIR)/x86_64/darwin/com_swoval_files_apple_FileEventMonitorImpl.o: $(NPMDIR)/swoval_apple_file_system.hpp
//$(BUILD_DIR)/x86_64/darwin/com_swoval_files_apple_FileEventMonitorImpl.o: apple/com_swoval_files_apple_FileEventMonitorImpl.cc
//mkdir -p $(BUILD_DIR)/x86_64/darwin; \
//$(CC) -c $< $(CCFLAGS) -framework Carbon -o $@
//
//$(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib: $(BUILD_DIR)/x86_64/darwin/com_swoval_files_apple_FileEventMonitorImpl.o \
//$(BUILD_DIR)/x86_64/darwin/$(QUICKLIST_SOURCE).o
//mkdir -p $(TARGET_DIR)/x86_64; \
//$(LD) -dynamiclib -framework Carbon $(CCFLAGS) -Wl,-headerpad_max_install_names -install_name @rpath/$(POSIX_LIB_NAME) \
//$(BUILD_DIR)/x86_64/darwin/com_swoval_files_apple_FileEventMonitorImpl.o \
//$(BUILD_DIR)/x86_64/darwin/$(QUICKLIST_SOURCE).o  \
//-o $@ ; \
//mkdir -p ../resources/native/x86_64; \
//cp $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib ../resources/native/x86_64
//
//
//
//$(TARGET_DIR)/x86_64/$(LIB_NAME).dll: $(BUILD_DIR)/x86_64/windows/$(QUICKLIST_SOURCE).o
//mkdir -p $(TARGET_DIR)/x86_64; \
//$(WIN64CC) $(LINUX_LDFLAGS) $< $(CCFLAGS) -Wl,-headerpad_max_install_names -o $@ \
//-D__WIN__ \
//-Wall -Wextra \
//-nostdlib -ffreestanding -mconsole -Os \
//-fno-stack-check -fno-stack-protector -mno-stack-arg-probe \
//-fno-leading-underscore \
//-lkernel32;
//mkdir -p ../resources/native/x86_64; \
//cp $(TARGET_DIR)/x86_64/$(LIB_NAME).dll ../resources/native/x86_64
//
//$(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so: $(BUILD_DIR)/x86_64/linux/$(QUICKLIST_SOURCE).o
//mkdir -p $(TARGET_DIR)/x86_64; \
//$(CC) -shared $< $(CCFLAGS) -Wl,-headerpad_max_install_names -o $@; \
//mkdir -p ../resources/native/x86_64; \
//cp $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so ../resources/native/x86_64
//
//$(TARGET_DIR)/x86_64/freebsd/$(POSIX_LIB_NAME).so: $(BUILD_DIR)/x86_64/freebsd/$(QUICKLIST_SOURCE).o
//mkdir -p $(TARGET_DIR)/x86_64/freebsd; \
//$(CC) -shared $< $(CCFLAGS) -Wl,-headerpad_max_install_names -o $@; \
//mkdir -p ../resources/native/x86_64/freebsd; \
//cp $(TARGET_DIR)/x86_64/freebsd/$(POSIX_LIB_NAME).so ../resources/native/x86_64/freebsd
//
//clean:
//rm -rf $(TARGET_DIR)/build $(TARGET_DIR)/$(NATIVE)
