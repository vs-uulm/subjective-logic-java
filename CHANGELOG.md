# Changelog for Subjective Logic

## Version 1.1.4

This version introduces a minimum and a majority fusion. These operations basically represent decision making based on multiple evidence sources.
In the minimum case, the projection of all opinions is used to decide the output (i.e., lowest expected probability).
In the majority case, the output is the majority decision as a dogmatic or vacuous opinion.
Simple tests are included with this release that show the desired output.

## Version 1.1.3a

This bugfix release deprecates and partially removes old, possibly unreliable code that was included in 1.1.3.

## Version 1.1.3

This version adds consensus and compromise fusion as per the same FUSION 2018 paper that is under review.

## Version 1.1.2

This version contains a corrected implementation of WBF for multiple sources.
It also splits up the tests, and adds tests for WBF.

The implementation of multi-source WBF is based on a generalization that is currently under review at the FUSION 2018 conference.

## Version 1.1.1

Updated the CBF implementation to be more efficient and closer to the corrected paper. Also adds some documentation.
A contributors file was added (previously missing).

## Version 1.1

This version includes a significant refactor of features, a bunch of bug fixes, and some tests, which have been written over the last 5 years.

