cmake_minimum_required(VERSION 3.7.0)

include_directories(src/main/cpp/include/)

add_library(
		app

		SHARED
		source_file.cpp
)

target_link_libraries(
		app
		log
)