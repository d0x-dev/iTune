cmake_minimum_required(VERSION 3.22.1)
project(itune_artwork LANGUAGES CXX)

add_library(itune_artwork SHARED dynamic_artwork.cpp)

find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(itune_artwork PRIVATE ${android-lib} ${jnigraphics-lib})
target_compile_options(itune_artwork PRIVATE -Wall -Wextra $<$<CONFIG:Release>:-O3>)
