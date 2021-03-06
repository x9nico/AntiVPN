package me.egg82.antivpn.commands.internal;

import co.aikar.commands.CommandIssuer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import me.egg82.antivpn.api.APIException;
import me.egg82.antivpn.api.VPNAPIProvider;
import me.egg82.antivpn.api.model.source.Source;
import me.egg82.antivpn.api.model.source.SourceManager;
import me.egg82.antivpn.api.model.source.models.SourceModel;
import me.egg82.antivpn.config.ConfigUtil;
import me.egg82.antivpn.lang.Message;
import me.egg82.antivpn.utils.DNSUtil;
import me.egg82.antivpn.utils.ExceptionUtil;
import org.checkerframework.checker.nullness.qual.NonNull;

public class ScoreCommand extends AbstractCommand {
    private final String sourceName;

    private static final DecimalFormat format = new DecimalFormat("##0.00");

    public ScoreCommand(@NonNull ProxyServer proxy, @NonNull CommandIssuer issuer, @NonNull String sourceName) {
        super(proxy, issuer);
        this.sourceName = sourceName;
    }

    public void run() {
        issuer.sendInfo(Message.SCORE__BEGIN, "{source}", sourceName);

        SourceManager sourceManager = VPNAPIProvider.getInstance().getSourceManager();

        Source<? extends SourceModel> source = sourceManager.getSource(sourceName);
        if (source == null) {
            issuer.sendError(Message.ERROR__INTERNAL);
            return;
        }

        issuer.sendInfo(Message.SCORE__TYPE, "{type}", "NordVPN");
        test(source, "NordVPN", DNSUtil.getNordVpnIps());
        issuer.sendInfo(Message.SCORE__SLEEP);
        try {
            Thread.sleep(60000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        issuer.sendInfo(Message.SCORE__TYPE, "{type}", "Cryptostorm");
        test(source, "Cryptostorm", DNSUtil.getCryptostormIps());
        issuer.sendInfo(Message.SCORE__SLEEP);
        try {
            Thread.sleep(60000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        issuer.sendInfo(Message.SCORE__TYPE, "{type}", "random home IPs");
        test(source, "Random home IP", DNSUtil.getHomeIps(), true);

        issuer.sendInfo(Message.SCORE__END, "{source}", source.getName());
    }

    private void test(@NonNull Source<? extends SourceModel> source, @NonNull String vpnName, @NonNull Set<String> ips) {
        test(source, vpnName, ips, false);
    }

    private void test(@NonNull Source<? extends SourceModel> source, @NonNull String vpnName, @NonNull Set<String> ips, boolean flipResult) {
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Testing against " + vpnName);
        }

        double error = 0.0d;
        double good = 0.0d;

        int i = 0;
        for (String ip : ips) {
            i++;
            try {
                if (source.getName().equalsIgnoreCase("getipintel")) {
                    Thread.sleep(5000L); // 15req/min max, so every 4 seconds. 5 to be safe.
                } else {
                    Thread.sleep(1000L);
                }
            } catch (IllegalArgumentException ex) {
                logger.error(ex.getMessage(), ex);
            } catch (InterruptedException ex) {
                logger.error(ex.getMessage(), ex);
                Thread.currentThread().interrupt();
            }

            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Testing " + ip + " (" + i + "/" + ips.size() + ")");
            }

            boolean result;
            try {
                result = Boolean.TRUE.equals(source.getResult(ip).get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                error += 1;
                continue;
            } catch (ExecutionException | CancellationException ex) {
                ExceptionUtil.handleException(ex, logger);
                if (!(ex.getCause() instanceof APIException) || !((APIException) ex.getCause()).isHard()) {
                    error += 1;
                }
                continue;
            }

            if ((!flipResult && result) || (flipResult && !result)) {
                good++;
            }
        }

        if (error > 0) {
            issuer.sendInfo(Message.SCORE__ERROR, "{source}", source.getName(), "{type}", vpnName, "{percent}", format.format((error / ips.size()) * 100.0d));
        }
        issuer.sendInfo(Message.SCORE__SCORE, "{source}", source.getName(), "{type}", vpnName, "{percent}", format.format((good / ips.size()) * 100.0d));
    }
}
