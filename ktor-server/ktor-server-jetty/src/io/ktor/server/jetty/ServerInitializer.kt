package io.ktor.server.jetty

import io.ktor.server.engine.*
import org.eclipse.jetty.alpn.server.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.*

internal fun Server.initializeServer(environment: ApplicationEngineEnvironment) {
    connectors = environment.connectors.map { ktorConnector ->
        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false

            if (ktorConnector.type == ConnectorType.HTTPS) {
                addCustomizer(SecureRequestCustomizer())
            }
        }

        val alpnAvailable = try {
            NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable()
            true
        } catch (e: Throwable) {
            false
        }

        val connectionFactories = when (ktorConnector.type) {
            ConnectorType.HTTP -> arrayOf(HttpConnectionFactory(httpConfig), HTTP2CServerConnectionFactory(httpConfig))
            ConnectorType.HTTPS -> arrayOf(SslConnectionFactory(SslContextFactory().apply {
                if (alpnAvailable) {
                    cipherComparator = HTTP2Cipher.COMPARATOR
                    isUseCipherSuitesOrder = true
                }

                keyStore = (ktorConnector as EngineSSLConnectorConfig).keyStore
                setKeyManagerPassword(String(ktorConnector.privateKeyPassword()))
                setKeyStorePassword(String(ktorConnector.keyStorePassword()))

                setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA")
            }, if (alpnAvailable) "alpn" else HttpVersion.HTTP_1_1.asString()),
                    if (alpnAvailable) {
                        ALPNServerConnectionFactory().apply {
                            defaultProtocol = HttpVersion.HTTP_1_1.asString()
                        }
                    } else null,
                    if (alpnAvailable) HTTP2ServerConnectionFactory(httpConfig) else HTTP2CServerConnectionFactory(httpConfig),
                    HttpConnectionFactory(httpConfig)).filterNotNull().toTypedArray()
            else -> throw IllegalArgumentException("Connector type ${ktorConnector.type} is not supported by Jetty engine implementation")
        }

        ServerConnector(this, *connectionFactories).apply {
            host = ktorConnector.host
            port = ktorConnector.port
        }
    }.toTypedArray()
}