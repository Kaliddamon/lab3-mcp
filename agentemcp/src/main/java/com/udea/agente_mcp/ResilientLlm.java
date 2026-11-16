package com.udea.agente_mcp;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM resiliente: reintenta con espera exponencial ante 503/429 y,
 * si el modelo sigue fallando, pasa automáticamente al siguiente de la lista.
 */
public class ResilientLlm extends BaseLlm {

    private final List<BaseLlm> models;
    private final int retriesPerModel;

    public ResilientLlm(List<BaseLlm> models, int retriesPerModel) {
        super(models.get(0).model());
        this.models = models;
        this.retriesPerModel = retriesPerModel;
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
        System.err.println(">>> ResilientLlm.generateContent llamado");
        return attempt(0, request, stream);
    }

    private Flowable<LlmResponse> attempt(int index, LlmRequest request, boolean stream) {
        BaseLlm llm = models.get(index);
        LlmRequest req = request.toBuilder().model(llm.model()).build();

        Flowable<LlmResponse> flow = Flowable
                .defer(() -> llm.generateContent(req, stream))
                .retryWhen(errors -> errors
                        .zipWith(Flowable.range(1, retriesPerModel + 1), (e, n) -> {
                            if (n > retriesPerModel || !isTransient(e)) {
                                throw asRuntime(e);
                            }
                            System.err.println("[" + llm.model() + "] error transitorio, reintento "
                                    + n + "/" + retriesPerModel);
                            return n;
                        })
                        .flatMap(n -> Flowable.timer((long) Math.pow(2, n), TimeUnit.SECONDS)));

        if (index + 1 < models.size()) {
            return flow.onErrorResumeNext(e -> {
                System.err.println("[" + llm.model() + "] falló (" + e.getMessage()
                        + "). Usando modelo de respaldo: " + models.get(index + 1).model());
                return attempt(index + 1, request, stream);
            });
        }
        return flow;
    }

    private static boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = String.valueOf(t.getMessage());
            if (m.contains("503") || m.contains("429")
                    || m.contains("high demand") || m.contains("UNAVAILABLE")) {
                return true;
            }
        }
        return false;
    }

    private static RuntimeException asRuntime(Throwable e) {
        return (e instanceof RuntimeException r) ? r : new RuntimeException(e);
    }

    @Override
    public BaseLlmConnection connect(LlmRequest request) {
        return models.get(0).connect(request);
    }
}