package com.hamza.account.manual;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * What the cover and the footer say, and where the pictures are.
 *
 * @param productName the name printed on the cover
 * @param version     the build this manual describes - the footer carries it on every page, so a
 *                    copy found on a customer's desk can be checked against what they are running
 * @param date        the day it was produced
 * @param imagesDir   where a figure's id resolves to {@code <id>.png}
 */
public record ManualMeta(String productName, String version, LocalDate date, Path imagesDir) {
}
