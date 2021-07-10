package edu.wpi.first.gradlerio.frcvision

import groovy.transform.CompileStatic
import jaci.gradle.deploy.context.DeployContext
import javax.inject.Inject
import jaci.gradle.deploy.target.location.SshDeployLocation
import org.apache.log4j.Logger
import org.gradle.api.Project
import edu.wpi.first.gradlerio.frc.InvalidImageException

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class Pi extends FRCVisionCompatibleTarget {

    private Logger log;

    @Inject
    Pi(String name, Project project) {
        super(name, project)
        log = Logger.getLogger(this.toString())

        this.directory = '/home/pi'

        this.maxChannels = 4

        this.onlyIf = { DeployContext ctx ->
            verifyOnlyIf(ctx)
        }
    }

    void setTeam(int team) {
        setAddresses(
            "raspberrypi.local".toString(),                 // Default mDNS
            "10.${(int)(team / 100)}.${team % 100}.11".toString(),   // 10.TE.AM.11 (default static IP)

            // Remaining cases are for weird environments, like a home network, practice field or otherwise.
            "frcvision".toString(),                       // Default DNS
            "frcvision.lan".toString(),                   // LAN mDNS/DNS
            "frcvision.frc-field.local".toString()        // Practice Field mDNS
        )
    }

    void setAddresses(String... addresses) {
        this.locations.clear()
        addresses.each { String addr ->
            this.addAddress(addr)
        }
    }

    void addAddress(String address) {
        this.getLocations().location(SshDeployLocation) { SshDeployLocation loc ->
            loc.setAddress(address)
            loc.setIpv6(false)
            loc.setUser("pi")
            loc.setPassword("raspberry")
        }
    }

    boolean verifyOnlyIf(DeployContext ctx) {
        ctx.logger.silent(true)
        if (checkImage) {
            log.info("Checking image...")
            readAndVerifyImage(ctx);
        }
        return true
    }

    void readAndVerifyImage(DeployContext context) {
        final String imageFile = "/etc/rpi-issue"
        final Pattern pattern = Pattern.compile("^FRCVision v([0-9a-zA-Z.-]+).*")

        String content = context.execute("cat ${imageFile}")
        log.info("Received Image File: ")
        log.info(content)

        boolean imageFound = false
        content.split("\n").each { String line ->
            log.info(line)
            Matcher matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                String imageGroup = matcher.group(1)
                log.info("Matched version: " + imageGroup)
                verifyImageVersion(imageGroup)
                imageFound = true
            }
        }

        if (!imageFound) {
            throw new InvalidImageException()
        }
    }

    boolean checkImage = false
    List<String> validImageVersions = []

    void verifyImageVersion(String image) {
        if (!validImageVersions.contains(image))
            throw new InvalidImageException(image, validImageVersions)
    }

    @Override
    String toString() {
        return "Pi[${name}]".toString()
    }
}
