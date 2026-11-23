/**
 * iTune Smooth Scroll & Parallax Engine (Hermes-Style)
 * High-performance scroll orchestration powered by Lenis & GPU CSS custom properties
 */
(() => {
  'use strict';

  // Check user preference for reduced motion
  const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (prefersReduced) return;

  // Initialize Lenis Smooth Scroll
  function initLenis() {
    if (typeof window.Lenis === 'undefined') {
      console.warn('[iTune] Lenis not detected, fallback to standard scrolling.');
      return null;
    }

    const lenis = new window.Lenis({
      duration: 1.25,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      orientation: 'vertical',
      gestureOrientation: 'vertical',
      smoothWheel: true,
      wheelMultiplier: 1.0,
      touchMultiplier: 1.2,
      infinite: false,
    });

    window.__lenis = lenis;

    function raf(time) {
      lenis.raf(time);
      requestAnimationFrame(raf);
    }
    requestAnimationFrame(raf);

    return lenis;
  }

  const lenis = initLenis();

  // Splash Screen Lock Management
  const splash = document.getElementById('site-splash');
  if (splash && lenis) {
    if (!splash.hidden) {
      lenis.stop();

      const observer = new MutationObserver(() => {
        if (splash.hidden || splash.classList.contains('splash-exit')) {
          lenis.start();
          observer.disconnect();
        }
      });

      observer.observe(splash, { attributes: true, attributeFilter: ['hidden', 'class'] });
    }
  }

  // DOM references for parallax & sticky effects
  const docRoot = document.documentElement;
  const masthead = document.querySelector('.masthead');
  const heroParallax = document.querySelector('.device-frame');
  if (heroParallax) {
    heroParallax.classList.add('itune-hero-parallax');
  }

  const parallaxTargets = Array.from(document.querySelectorAll('.itune-parallax'));
  const curtainMount = document.querySelector('.site-footer-curtain-mount');
  const curtainSpacer = document.querySelector('.site-footer-curtain-spacer');

  // Core RAF-Driven Scroll Updates
  function updateScrollEffects() {
    const scrollY = window.scrollY || window.pageYOffset || 0;
    const vh = window.innerHeight;

    // 1. Hero fade/blur progress (Hermes Agent --hw-hero-progress)
    const heroProgress = Math.min(Math.max(scrollY / 420, 0), 1);
    docRoot.style.setProperty('--hw-hero-progress', heroProgress.toFixed(3));

    // 2. Background parallax drift (Hermes deep multi-plane space)
    const bgOffset = (scrollY * 0.12).toFixed(1);
    docRoot.style.setProperty('--py-bg', `${bgOffset}px`);

    // 3. Sticky Frosted Glass Masthead
    if (masthead) {
      if (scrollY > 70) {
        masthead.classList.add('is-sticky');
      } else {
        masthead.classList.remove('is-sticky');
      }
    }

    // 4. Hero Differential Parallax
    if (heroParallax) {
      const heroOffset = Math.min(Math.max(scrollY * 0.12, 0), 180);
      heroParallax.style.setProperty('--py-hero', `${heroOffset.toFixed(1)}px`);
    }

    // 5. Section Aperture Parallax
    if (parallaxTargets.length > 0) {
      const vCenter = vh / 2;
      for (let i = 0; i < parallaxTargets.length; i++) {
        const el = parallaxTargets[i];
        const rect = el.getBoundingClientRect();
        if (rect.bottom > -120 && rect.top < vh + 120) {
          const elCenter = rect.top + rect.height / 2;
          const speed = parseFloat(el.getAttribute('data-parallax-speed') || '0.12');
          const offset = (elCenter - vCenter) * speed;
          el.style.setProperty('--py-parallax', `${offset.toFixed(1)}px`);
        }
      }
    }

    // 6. Sticky Curtain Reveal / Footer Lift Effect
    if (curtainMount && curtainSpacer && window.innerWidth > 768) {
      const spacerRect = curtainSpacer.getBoundingClientRect();
      const distance = vh - spacerRect.top;
      const progress = Math.min(Math.max(distance / spacerRect.height, 0), 1);
      const currentLift = ((1 - progress) * 200).toFixed(1);
      const opacity = progress.toFixed(3);
      const pe = progress > 0.85 ? 'auto' : 'none';

      curtainMount.style.setProperty('--itune-footer-lift', `${currentLift}px`);
      curtainMount.style.setProperty('--itune-footer-opacity', opacity);
      curtainMount.style.setProperty('--itune-footer-pe', pe);
    }
  }

  // Hook scroll updates
  if (lenis) {
    lenis.on('scroll', updateScrollEffects);
  } else {
    window.addEventListener('scroll', updateScrollEffects, { passive: true });
  }

  window.addEventListener('resize', updateScrollEffects, { passive: true });
  document.addEventListener('DOMContentLoaded', updateScrollEffects);
  updateScrollEffects();

  // Intercept anchor clicks for Lenis smooth navigation
  document.addEventListener('click', (e) => {
    const anchor = e.target.closest('a[href^="#"]');
    if (!anchor) return;

    const href = anchor.getAttribute('href');
    if (href && href.length > 1) {
      const target = document.querySelector(href);
      if (target) {
        e.preventDefault();
        if (lenis) {
          lenis.scrollTo(target, { offset: -40, duration: 1.2 });
        } else {
          target.scrollIntoView({ behavior: 'smooth' });
        }
        try {
          history.pushState(null, '', href);
        } catch (_) {}
      }
    }
  });

  // Modal Observer: Pause Lenis when any modal or dialog is open
  const bodyObserver = new MutationObserver(() => {
    const hasActiveModal = document.querySelector('.release-modal-backdrop, [role="dialog"][aria-modal="true"]');
    if (lenis) {
      if (hasActiveModal) {
        lenis.stop();
      } else {
        lenis.start();
      }
    }
  });
  bodyObserver.observe(document.body, { childList: true, subtree: true });

})();
