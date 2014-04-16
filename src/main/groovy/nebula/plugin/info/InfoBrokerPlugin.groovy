package nebula.plugin.info

import groovy.transform.Canonical
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Broker between Collectors and Reporters. Collectors report to this plugin about manifest values,
 * Reporters call this plugin to get values.
 *
 * TBD Allow user build scripts to easily provide values, maybe via an extension
 * TBD Provide DomainObjectCollection like services, so projects can react to values being resolved. It has to be resolved, or else
 *     the act of reacting to values could cause resolving too early.
 */
class InfoBrokerPlugin implements Plugin<Project> {

    List<ManifestEntry> container

    void apply(Project project) {

        container = new ArrayList<ManifestEntry>()

        // Leaving out for now. I find that the configure methods, when called this way, aren't being called.
        //project.getExtensions().add('manifest', container)

        // The idea is that other plugins will grab a reference to this plugin or manifestEntry and add their entries
    }

    def add(String key, Closure closure) {
        def entry = new ManifestEntry(key, closure)
        container.add( entry )
        entry
    }

    def add(String key, Object value) {
        def entry = new ManifestEntry(key, value)
        container.add( entry )
        entry
    }

    Map<String, String> buildNonChangingManifest() {
        return collectEntries(container.findAll { it.changing == false })
    }

    Map<String, String> buildManifest() {
        return collectEntries(container)
    }

    private Map<String,String> collectEntries(Collection<ManifestEntry> entries) {

        // We can't validate via all() because multiple calls would leave the all's closure around
        (Map<String, String>) entries.collectEntries { ManifestEntry entry ->

            resolve(entry)
            return [entry.name, entry.value]
        }.findAll {
            it.value != null
        }.collectEntries {
            [it.key, it.value.toString()]
        }
    }

    /**
     * Resolves entry, forcing a call to the valueProvided if needed, then caching it.
     */
    private void resolve(ManifestEntry entry) {
        // Validate, we can't do this earlier since objects are configured after being added.
        if (!(entry.value || entry.valueProvider)) {
            throw new GradleException("Manifest entry (${entry.name}) is missing a value")
        }

        if (!entry.value && entry.valueProvider) {
            // Force resolution
            def value = entry.valueProvider.call()
            if (!entry.changing) {
                // And then cache value, even nulls
                entry.value = value
            }
        }
    }

    static getPlugin(Project project) {
        return project.getPlugins().getPlugin(InfoBrokerPlugin)
    }

    String buildManifestString() {
        def attrs = buildManifest()
        def manifestStr = attrs.collect { "${it.key}: ${it.value}"}.join('\n      ')
        return manifestStr
    }

    String buildString(String indent = '') {
        def attrs = buildManifest()
        def manifestStr = attrs.collect { "${indent}${it.key}: ${it.value}"}.join('\n')
        return manifestStr
    }

    /**
     * Very limited accessor. It requires that the key value already exists. Best for testing.
     *
     * @param key
     * @return String based value of entry
     */
    String buildEntry(String key) {
        def entry = container.find { it.name == key }
        if (!entry) throw new IllegalArgumentException("Unable to find $key")

        resolve(entry)
        return entry.value

    }
    @Canonical
    static class ManifestEntry {
        /**
         * Constructor called by DomainNamedContainer
         * @param name
         */
        ManifestEntry(String name) {
            this.name = name
        }

        ManifestEntry(String name, Closure<String> valueProvider) {
            this.name = name
            this.valueProvider = valueProvider
        }

        ManifestEntry(String name, Object value, boolean changing = false) {
            this.name = name
            this.value = value
            this.changing = changing
        }

        final String name
        Object value // toString() will be called on it, on every buildManifest
        Closure<String> valueProvider
        boolean changing
    }
}
