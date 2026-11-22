package sg.andi.fusionlos.api;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import sg.andi.fusionlos.auth.CurrentUser;
import sg.andi.fusionlos.auth.DemoUser;
import sg.andi.fusionlos.auth.RequiresRole;
import sg.andi.fusionlos.auth.Role;
import sg.andi.fusionlos.projection.QueueProjection;
import sg.andi.fusionlos.projection.QueueProjection.QueueRow;

import java.util.List;

@RestController
@Profile("api")
@RequestMapping("/api/underwriting")
public class UnderwritingController {

    private final QueueProjection queue;

    public UnderwritingController(QueueProjection queue) {
        this.queue = queue;
    }

    /** Underwriters see their own cases. A senior underwriter sees the whole team's queue. */
    @GetMapping("/queue")
    @RequiresRole({Role.UNDERWRITER, Role.SENIOR_UNDERWRITER})
    public List<QueueRow> queue(HttpServletRequest http) {
        DemoUser user = CurrentUser.of(http);
        return queue.forUser(user.role() == Role.SENIOR_UNDERWRITER ? null : user.id());
    }
}
