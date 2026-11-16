// Experimental renderer adapter for pinned MIT lsfg-vk v1.0.0, SDR2x only.
#include <volk.h>
#include <rex/graphics/mcla_lsfg_api.h>
#include "config/config.hpp"
#include "extract/extract.hpp"
#include "extract/trans.hpp"
#include "v3_1p/context.hpp"

#include <algorithm>
#include <cstdio>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>

namespace Config { Configuration activeConf{}; }
namespace LSFG_3_1P {
struct RendererAccess {
    static LSFG::Core::Image& input(Context& context, unsigned index) {
        return index ? context.inImg_1 : context.inImg_0;
    }
    static LSFG::Core::Image& output(Context& context) { return context.generate.outImgs.at(0); }
    static void reset(Context& context) { context.frameIdx = 0; }
};
}

namespace {
std::mutex creation_mutex;
VkDevice active_device = VK_NULL_HANDLE;
unsigned active_contexts = 0;
std::string cached_dll;

void message(char* output, size_t size, const char* text) noexcept {
    if (output && size) std::snprintf(output, size, "%s", text);
}

struct Backend {
    MclaLsfgDevice description;
    LSFG::Vulkan vk;
    std::unique_ptr<LSFG_3_1P::Context> context;
    uint64_t frames = 0, last_submission = 0;
    bool failed = false;

    explicit Backend(const MclaLsfgDevice& input)
        : description(input),
          vk{.device{input.device, input.physical_device, input.queue_family, input.queue},
             .commandPool{}, .descriptorPool{}, .generationCount = 1, .flowScale = 1.0F,
             .isHdr = false, .shaders{}, .resources{}} {
        vk.commandPool = LSFG::Core::CommandPool(vk.device);
        vk.descriptorPool = LSFG::Core::DescriptorPool(vk.device);
        vk.resources = LSFG::Pool::ResourcePool(false, 1.0F);
        vk.shaders = LSFG::Pool::ShaderPool([](const std::string& name) {
            return Extract::translateShader(Extract::getShader(name));
        });
        context = std::make_unique<LSFG_3_1P::Context>(vk, -1, -1, std::vector<int>{},
            VkExtent2D{input.width, input.height}, VK_FORMAT_R8G8B8A8_UNORM);
    }
};

void transition(VkCommandBuffer command, VkImage image, VkImageLayout old_layout,
                VkImageLayout new_layout, VkAccessFlags destination_access) {
    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.srcAccessMask = old_layout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 :
        VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    barrier.dstAccessMask = destination_access;
    barrier.oldLayout = old_layout;
    barrier.newLayout = new_layout;
    barrier.srcQueueFamilyIndex = barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
}

void blit(VkCommandBuffer command, VkImage source, VkImage destination, uint32_t width, uint32_t height) {
    VkImageBlit region{};
    region.srcSubresource = region.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    region.srcOffsets[1] = region.dstOffsets[1] = {
        static_cast<int32_t>(width), static_cast<int32_t>(height), 1};
    vkCmdBlitImage(command, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        destination, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region, VK_FILTER_NEAREST);
}

bool supported_format(VkFormat format) {
    return format == VK_FORMAT_R8G8B8A8_UNORM || format == VK_FORMAT_R16G16B16A16_SFLOAT;
}
}

extern "C" void* mcla_lsfg_create(const MclaLsfgDevice* input, char* error, size_t error_size) {
    try {
        if (!input || input->abi_version != MCLA_LSFG_ABI_VERSION ||
            !input->instance || !input->physical_device || !input->device || !input->queue ||
            !input->get_instance_proc_addr || !input->dll_path || !*input->dll_path ||
            (input->enabled_features & MCLA_LSFG_REQUIRED_FEATURES) != MCLA_LSFG_REQUIRED_FEATURES ||
            input->width < 128 || input->height < 128 || input->width > 4096 || input->height > 4096)
            throw std::invalid_argument("Invalid LSFG device, enabled features, DLL or SDR2x extent");
        std::lock_guard<std::mutex> lock(creation_mutex);
        if (active_contexts && active_device != input->device)
            throw std::runtime_error("Only one renderer device per LSFG module is supported");
        if (!cached_dll.empty() && cached_dll != input->dll_path)
            throw std::runtime_error("Changing the DLL requires restarting the process");
        if (!active_contexts) {
            volkInitializeCustom(input->get_instance_proc_addr);
            volkLoadInstance(input->instance);
            volkLoadDevice(input->device);
        }
        for (VkFormat format : {VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R16G16B16A16_SFLOAT}) {
            VkFormatProperties props{};
            vkGetPhysicalDeviceFormatProperties(input->physical_device, format, &props);
            const VkFormatFeatureFlags wanted = VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK_FORMAT_FEATURE_BLIT_DST_BIT;
            if ((props.optimalTilingFeatures & wanted) != wanted)
                throw std::runtime_error("Required image blit format unsupported");
        }
        Config::activeConf.dll = input->dll_path;
        Extract::extractShaders();
        cached_dll = input->dll_path;
        auto context = std::make_unique<Backend>(*input);
        active_device = input->device;
        ++active_contexts;
        message(error, error_size, "");
        return context.release();
    } catch (const std::exception& failure) {
        message(error, error_size, failure.what());
        return nullptr;
    } catch (...) {
        message(error, error_size, "Unknown LSFG initialization failure");
        return nullptr;
    }
}

extern "C" int mcla_lsfg_record(void* opaque, const MclaLsfgFrame* frame, int* ready,
                                char* error, size_t error_size) {
    if (ready) *ready = 0;
    auto* backend = static_cast<Backend*>(opaque);
    try {
        if (!backend || backend->failed || !frame || !ready || !frame->command_buffer ||
            !frame->source || !frame->destination || frame->source == frame->destination ||
            !frame->submission || frame->submission < backend->last_submission ||
            frame->source_layout == VK_IMAGE_LAYOUT_UNDEFINED ||
            !supported_format(frame->source_format) || !supported_format(frame->destination_format))
            throw std::invalid_argument("Invalid LSFG recording request or failed context");
        // Caller serializes records and supplies the completed submission to destroy.
        backend->last_submission = frame->submission;
        if (frame->reset_history) {
            backend->frames = 0;
            LSFG_3_1P::RendererAccess::reset(*backend->context);
        }
        auto& source = LSFG_3_1P::RendererAccess::input(*backend->context, backend->frames % 2);
        const auto command = frame->command_buffer;
        transition(command, frame->source, frame->source_layout,
                   VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT);
        transition(command, source.handle(), source.getLayout(),
                   VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT);
        blit(command, frame->source, source.handle(), backend->description.width, backend->description.height);
        transition(command, frame->source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                   frame->source_layout, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
        transition(command, source.handle(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                   VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_READ_BIT);
        source.setLayout(VK_IMAGE_LAYOUT_GENERAL);
        LSFG::Core::CommandBuffer view(command);
        backend->context->record(backend->vk, view);
        const bool mature = backend->frames++ >= 3;
        if (mature) {
            auto& output = LSFG_3_1P::RendererAccess::output(*backend->context);
            transition(command, output.handle(), output.getLayout(),
                       VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT);
            transition(command, frame->destination, frame->destination_layout,
                       VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT);
            blit(command, output.handle(), frame->destination, backend->description.width, backend->description.height);
            transition(command, output.handle(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                       VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
            output.setLayout(VK_IMAGE_LAYOUT_GENERAL);
            transition(command, frame->destination, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                       VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_READ_BIT);
            *ready = 1;
        }
        message(error, error_size, "");
        return 0;
    } catch (const std::exception& failure) {
        if (backend) backend->failed = true;
        message(error, error_size, failure.what());
        return -1;
    } catch (...) {
        if (backend) backend->failed = true;
        message(error, error_size, "Unknown LSFG recording failure");
        return -1;
    }
}

extern "C" int mcla_lsfg_destroy(void* opaque, uint64_t completed_submission) {
    auto* backend = static_cast<Backend*>(opaque);
    if (!backend) return 0;
    if (completed_submission < backend->last_submission) return 1;
    std::lock_guard<std::mutex> lock(creation_mutex);
    delete backend;
    if (--active_contexts == 0) active_device = VK_NULL_HANDLE;
    return 0;
}
